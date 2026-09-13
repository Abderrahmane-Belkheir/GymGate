package com.GymGate.Ai.detection;

import ai.onnxruntime.*;
import org.opencv.core.*;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.nio.FloatBuffer;
import java.util.*;

/**
 * SCRFD face detector backed by ONNX Runtime (Java) and OpenCV for
 * image pre/post-processing.
 *
 * Usage:
 * <pre>
 *   OnnxModel onnxModel = new OnnxModel(Path.of("scrfd.onnx"), OnnxModel.Role.DETECTOR);
 *   SCRFDFaceDetector detector = new SCRFDFaceDetector(onnxModel.session(), 0.5f, 0.4f);
 *   List&lt;FaceBox&gt; faces = detector.detect(frameMat);
 *   // detector.close() is a no-op — close onnxModel.session() when you're
 *   // actually done with the model.
 * </pre>
 *
 * The detector introspects the ONNX graph on load to determine the model's
 * expected input size and the stride/anchor layout of its outputs, so it
 * does not need to be told the exact SCRFD variant in advance.
 *
 * PERFORMANCE NOTE: this class is NOT thread-safe. It reuses internal Mats
 * and pixel buffers across calls to avoid per-frame allocation/GC churn —
 * the same tradeoff ArcFaceRecognizer already makes. That's fine for the
 * sequential detect -> align -> recognize pipeline this is built for; do
 * not call detect() from multiple threads concurrently on the same
 * instance (use one instance per thread if you ever need that).
 */
public class SCRFDFaceDetector implements AutoCloseable {

    // ---- tunables ----
    private final float scoreThreshold;
    private final float nmsThreshold;

    // ---- low-light handling ----
    // Only kicks in when a frame is actually dark, to avoid wasting CPU on
    // every frame in a well-lit entrance.
    private boolean lowLightEnhancementEnabled = true;
    private double lowLightMeanBrightnessThreshold = 90.0; // 0-255 scale
    private final CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));

    // Reused low-light-enhancement scratch Mats (only touched on dark frames).
    private final Mat grayScratch = new Mat();
    private final Mat labScratch = new Mat();
    private final Mat labLScratch = new Mat();
    private final Mat enhancedScratch = new Mat();
    private final List<Mat> labChannelsScratch = new ArrayList<>(3);

    // ---- onnxruntime ----
    // Both are owned by the caller (e.g. your OnnxModel/OnnxRuntimeManager) —
    // this class never closes them.
    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;
    private final int inputWidth;
    private final int inputHeight;

    // ---- reused per-frame scratch state (this is the whole point: avoid
    // allocating a fresh Mat / float[] on every camera frame) ----
    private final Mat resizedScratch = new Mat();
    private final Mat canvasScratch;             // inputHeight x inputWidth, BGR, zeroed once, refilled per frame
    private final byte[] canvasBytes;             // raw BGR bytes pulled out of canvasScratch, single native copy per frame
    private final float[] chwScratch;              // normalized, planar (CHW), RGB-ordered network input
    private int lastLetterboxW = -1;              // tracks the letterboxed region so we only re-zero padding
    private int lastLetterboxH = -1;              // when the source frame's aspect ratio actually changes

    // ---- per-stride output grouping, resolved once from the graph ----
    private static class StrideGroup {
        int stride;
        int numAnchors;
        String scoreOutputName;
        String bboxOutputName;
        String kpsOutputName; // nullable
        float[] anchorCenters; // flattened (N*2): x0,y0,x1,y1,...
    }

    private final List<StrideGroup> strideGroups = new ArrayList<>();
    private final boolean modelHasLandmarks;

    /**
     * Wraps an already-created OrtSession (e.g. from your OnnxModel.session()).
     * This class does not own the session or environment and will not close
     * them — that stays the responsibility of whatever created them
     * (OnnxRuntimeManager, in your case).
     *
     * @param session an OrtSession already opened against your SCRFD .onnx file
     */
    public SCRFDFaceDetector(OrtSession session, float scoreThreshold, float nmsThreshold,
                             int inputWidth, int inputHeight) throws OrtException {
        this.scoreThreshold = scoreThreshold;
        this.nmsThreshold = nmsThreshold;
        this.session = session;
        this.env = OrtEnvironment.getEnvironment();
        this.inputName = session.getInputInfo().keySet().iterator().next();

        this.inputHeight = inputHeight;
        this.inputWidth = inputWidth;

        this.canvasScratch = Mat.zeros(this.inputHeight, this.inputWidth, CvType.CV_8UC3);
        this.canvasBytes = new byte[this.inputHeight * this.inputWidth * 3];
        this.chwScratch = new float[3 * this.inputHeight * this.inputWidth];

        Map<String, NodeInfo> outputInfo = session.getOutputInfo();
        Map<Long, String> scoresBySize = new HashMap<>();
        Map<Long, String> bboxBySize = new HashMap<>();
        Map<Long, String> kpsBySize = new HashMap<>();
        for (Map.Entry<String, NodeInfo> e : outputInfo.entrySet()) {
            TensorInfo ti = (TensorInfo) e.getValue().getInfo();
            long[] shape = ti.getShape();
            long lastDim = shape[shape.length - 1];
            if (lastDim == 1) scoresBySize.put(sizeKeyFor(e.getKey(), shape), e.getKey());
            else if (lastDim == 4) bboxBySize.put(sizeKeyFor(e.getKey(), shape), e.getKey());
            else if (lastDim == 10) kpsBySize.put(sizeKeyFor(e.getKey(), shape), e.getKey());
        }
        this.modelHasLandmarks = !kpsBySize.isEmpty();
        runDummyInferenceAndBuildGroups(scoresBySize, bboxBySize, kpsBySize);
    }

    private long sizeKeyFor(String name, long[] shape) {
        // Placeholder grouping key when shape is static; refined after dummy run.
        return shape.length >= 2 && shape[shape.length - 2] > 0 ? shape[shape.length - 2] : name.hashCode();
    }

    /**
     * Runs one inference on a blank frame purely to read back the actual
     * output tensor lengths (handles ONNX graphs exported with dynamic axes),
     * then groups outputs into per-stride (score, bbox, kps) triplets and
     * pre-computes anchor centers for each stride.
     */
    private void runDummyInferenceAndBuildGroups(Map<Long, String> scoresBySize,
                                                  Map<Long, String> bboxBySize,
                                                  Map<Long, String> kpsBySize) throws OrtException {
        float[] dummyChw = new float[3 * inputHeight * inputWidth];
        OnnxTensor dummyTensor = OnnxTensor.createTensor(env,
                FloatBuffer.wrap(dummyChw), new long[]{1, 3, inputHeight, inputWidth});

        Map<String, Long> actualCounts = new HashMap<>();
        try (OrtSession.Result result = session.run(Collections.singletonMap(inputName, dummyTensor))) {
            for (Map.Entry<String, OnnxValue> e : result) {
                OnnxTensor t = (OnnxTensor) e.getValue();
                long[] shape = t.getInfo().getShape();
                long count = shape[shape.length - 2];
                actualCounts.put(e.getKey(), count);
            }
        } finally {
            dummyTensor.close();
        }

        // Re-bucket by the REAL anchor count now that we've run inference.
        Map<Long, String> scoreByCount = new HashMap<>();
        Map<Long, String> bboxByCount = new HashMap<>();
        Map<Long, String> kpsByCount = new HashMap<>();
        for (Map.Entry<String, NodeInfo> e : session.getOutputInfo().entrySet()) {
            TensorInfo ti = (TensorInfo) e.getValue().getInfo();
            long lastDim = ti.getShape()[ti.getShape().length - 1];
            long count = actualCounts.get(e.getKey());
            if (lastDim == 1) scoreByCount.put(count, e.getKey());
            else if (lastDim == 4) bboxByCount.put(count, e.getKey());
            else if (lastDim == 10) kpsByCount.put(count, e.getKey());
        }

        int[] candidateStrides = {8, 16, 32, 64};
        for (Map.Entry<Long, String> scoreEntry : scoreByCount.entrySet()) {
            long count = scoreEntry.getKey();
            String bboxName = bboxByCount.get(count);
            if (bboxName == null) continue; // couldn't pair — skip malformed output

            StrideGroup g = new StrideGroup();
            g.scoreOutputName = scoreEntry.getValue();
            g.bboxOutputName = bboxName;
            g.kpsOutputName = kpsByCount.get(count);

            // Solve stride & numAnchors: count = numAnchors * (H/stride) * (W/stride)
            int bestStride = 8;
            int bestAnchors = 2;
            double bestErr = Double.MAX_VALUE;
            for (int s : candidateStrides) {
                long fh = inputHeight / s;
                long fw = inputWidth / s;
                if (fh <= 0 || fw <= 0) continue;
                for (int na = 1; na <= 3; na++) {
                    long predicted = fh * fw * na;
                    double err = Math.abs(predicted - count);
                    if (err < bestErr) {
                        bestErr = err;
                        bestStride = s;
                        bestAnchors = na;
                    }
                }
            }
            g.stride = bestStride;
            g.numAnchors = bestAnchors;
            g.anchorCenters = buildAnchorCenters(inputHeight, inputWidth, g.stride, g.numAnchors);
            strideGroups.add(g);
        }

        strideGroups.sort(Comparator.comparingInt(g -> g.stride));

        if (strideGroups.isEmpty()) {
            throw new IllegalStateException(
                    "Could not resolve SCRFD output layout from the ONNX model. " +
                    "Check that the model has matching score/bbox output pairs.");
        }
    }

    private static float[] buildAnchorCenters(int inputH, int inputW, int stride, int numAnchors) {
        int fh = inputH / stride;
        int fw = inputW / stride;
        float[] centers = new float[fh * fw * numAnchors * 2];
        int idx = 0;
        for (int y = 0; y < fh; y++) {
            for (int x = 0; x < fw; x++) {
                for (int a = 0; a < numAnchors; a++) {
                    centers[idx++] = x * stride;
                    centers[idx++] = y * stride;
                }
            }
        }
        return centers;
    }

    /**
     * Runs face detection on a single BGR OpenCV frame.
     *
     * @param frameBgr the camera frame, as read from your live stream (BGR, 8-bit, 3-channel)
     * @return detected faces in the ORIGINAL frame's coordinate space, sorted by descending score
     */
    public List<FaceBox> detect(Mat frameBgr) throws OrtException {
        if (frameBgr.empty()) return Collections.emptyList();

        Mat enhanced = enhanceIfLowLight(frameBgr);

        // ---- letterbox resize, preserving aspect ratio ----
        int origH = enhanced.rows();
        int origW = enhanced.cols();
        float imRatio = (float) origH / origW;
        float modelRatio = (float) inputHeight / inputWidth;
        int newW, newH;
        if (imRatio > modelRatio) {
            newH = inputHeight;
            newW = Math.round(newH / imRatio);
        } else {
            newW = inputWidth;
            newH = Math.round(newW * imRatio);
        }
        float detScale = (float) newH / origH;

        // canvasScratch is reused across frames and pre-zeroed once at
        // construction. Padding lives outside the top-left newW-by-newH
        // region we write into; since newW/newH are stable for a fixed camera feed
        // (same source resolution every frame), that padding is only ever
        // written once and stays zero on every subsequent frame — so we
        // only need to re-zero it if the letterbox geometry actually
        // changes (e.g. camera resolution/aspect ratio changes mid-stream).
        boolean letterboxed = newW != inputWidth || newH != inputHeight;
        boolean geometryChanged = newW != lastLetterboxW || newH != lastLetterboxH;
        if (letterboxed && geometryChanged) {
            canvasScratch.setTo(Scalar.all(0));
        }
        lastLetterboxW = newW;
        lastLetterboxH = newH;

        if (newW == inputWidth && newH == inputHeight) {
            // No letterbox padding needed at all — resize straight into the canvas.
            Imgproc.resize(enhanced, canvasScratch, new Size(newW, newH));
        } else {
            Imgproc.resize(enhanced, resizedScratch, new Size(newW, newH));
            resizedScratch.copyTo(canvasScratch.submat(new Rect(0, 0, newW, newH)));
        }

        // ---- single-pass preprocessing ----
        // Previously: cvtColor(BGR->RGB) + convertTo(32F) + get() = three
        // full-image passes (two native, one copy) before the CHW loop even
        // starts. We skip all of that: pull the canvas's raw BGR bytes out
        // in one native call, then do the R/B channel swap AND the
        // (x-127.5)/128 normalization AND the HWC->CHW planar reorder in a
        // single Java loop over the pixels.
        canvasScratch.get(0, 0, canvasBytes);
        bgrBytesToChwNormalized(canvasBytes, chwScratch, inputHeight, inputWidth);

        OnnxTensor inputTensor = OnnxTensor.createTensor(env,
                FloatBuffer.wrap(chwScratch), new long[]{1, 3, inputHeight, inputWidth});

        List<FaceBox> candidates = new ArrayList<>();
        try (OrtSession.Result result = session.run(Collections.singletonMap(inputName, inputTensor))) {
            for (StrideGroup g : strideGroups) {
                float[] scores = flatten1((OnnxTensor) result.get(g.scoreOutputName).get());
                float[] bboxDeltas = flatten1((OnnxTensor) result.get(g.bboxOutputName).get());
                float[] kpsDeltas = g.kpsOutputName != null
                        ? flatten1((OnnxTensor) result.get(g.kpsOutputName).get())
                        : null;

                int n = scores.length;
                for (int i = 0; i < n; i++) {
                    float score = scores[i];
                    if (score < scoreThreshold) continue;

                    float cx = g.anchorCenters[i * 2];
                    float cy = g.anchorCenters[i * 2 + 1];

                    float dx1 = bboxDeltas[i * 4] * g.stride;
                    float dy1 = bboxDeltas[i * 4 + 1] * g.stride;
                    float dx2 = bboxDeltas[i * 4 + 2] * g.stride;
                    float dy2 = bboxDeltas[i * 4 + 3] * g.stride;

                    float x1 = (cx - dx1) / detScale;
                    float y1 = (cy - dy1) / detScale;
                    float x2 = (cx + dx2) / detScale;
                    float y2 = (cy + dy2) / detScale;

                    float[] landmarks = null;
                    if (kpsDeltas != null) {
                        landmarks = new float[10];
                        for (int k = 0; k < 5; k++) {
                            float kdx = kpsDeltas[i * 10 + k * 2] * g.stride;
                            float kdy = kpsDeltas[i * 10 + k * 2 + 1] * g.stride;
                            landmarks[k * 2] = (cx + kdx) / detScale;
                            landmarks[k * 2 + 1] = (cy + kdy) / detScale;
                        }
                    }

                    candidates.add(new FaceBox(x1, y1, x2, y2, score, landmarks));
                }
            }
        } finally {
            inputTensor.close();
        }

        candidates.sort((a, b) -> Float.compare(b.score, a.score));
        return nonMaxSuppression(candidates, nmsThreshold);
    }

    /**
     * Converts interleaved BGR bytes (HWC, 8-bit) straight into normalized
     * planar RGB floats (CHW): swaps channel order, applies
     * (pixel - 127.5) / 128.0, and reorders HWC -> CHW, all in one pass.
     * Replaces what used to be cvtColor + convertTo + Mat#get + a separate
     * HWC->CHW loop.
     */
    private static void bgrBytesToChwNormalized(byte[] bgr, float[] chw, int h, int w) {
        int plane = h * w;
        for (int i = 0; i < plane; i++) {
            int base = i * 3;
            // Mat bytes are signed in Java; mask to get the unsigned 0-255 value.
            float b = (bgr[base] & 0xFF);
            float g = (bgr[base + 1] & 0xFF);
            float r = (bgr[base + 2] & 0xFF);
            chw[i] = (r - 127.5f) / 128.0f;          // R plane
            chw[plane + i] = (g - 127.5f) / 128.0f;  // G plane
            chw[2 * plane + i] = (b - 127.5f) / 128.0f; // B plane
        }
    }

    @SuppressWarnings("unchecked")
    private static float[] flatten1(OnnxTensor tensor) throws OrtException {
        FloatBuffer buf = tensor.getFloatBuffer();
        float[] out = new float[buf.remaining()];
        buf.get(out);
        return out;
    }

    private static List<FaceBox> nonMaxSuppression(List<FaceBox> boxes, float iouThreshold) {
        List<FaceBox> kept = new ArrayList<>();
        boolean[] suppressed = new boolean[boxes.size()];

        for (int i = 0; i < boxes.size(); i++) {
            if (suppressed[i]) continue;
            FaceBox a = boxes.get(i);
            kept.add(a);
            for (int j = i + 1; j < boxes.size(); j++) {
                if (suppressed[j]) continue;
                FaceBox b = boxes.get(j);
                if (iou(a, b) > iouThreshold) {
                    suppressed[j] = true;
                }
            }
        }
        return kept;
    }

    private static float iou(FaceBox a, FaceBox b) {
        float interX1 = Math.max(a.x1, b.x1);
        float interY1 = Math.max(a.y1, b.y1);
        float interX2 = Math.min(a.x2, b.x2);
        float interY2 = Math.min(a.y2, b.y2);
        float interW = Math.max(0, interX2 - interX1);
        float interH = Math.max(0, interY2 - interY1);
        float interArea = interW * interH;
        float areaA = a.width() * a.height();
        float areaB = b.width() * b.height();
        float union = areaA + areaB - interArea;
        return union <= 0 ? 0 : interArea / union;
    }

    public boolean modelHasLandmarks() {
        return modelHasLandmarks;
    }

    /**
     * Enable/disable automatic low-light enhancement (on by default).
     *
     * @param enabled              whether to enhance dark frames before detection
     * @param meanBrightnessThreshold frames with average brightness below this
     *                                (0-255 scale) get CLAHE-enhanced; frames
     *                                at or above it pass through untouched.
     *                                90 is a reasonable starting point for a
     *                                dim gym entrance; lower it if well-lit
     *                                frames are being needlessly enhanced,
     *                                raise it if dark frames are still missed.
     */
    public void setLowLightEnhancement(boolean enabled, double meanBrightnessThreshold) {
        this.lowLightEnhancementEnabled = enabled;
        this.lowLightMeanBrightnessThreshold = meanBrightnessThreshold;
    }

    /**
     * Measures a frame's average brightness (0-255). Handy for logging/
     * tuning {@link #setLowLightEnhancement} against your actual gate camera
     * feed at different times of day.
     */
    public double measureBrightness(Mat frameBgr) {
        Imgproc.cvtColor(frameBgr, grayScratch, Imgproc.COLOR_BGR2GRAY);
        return Core.mean(grayScratch).val[0];
    }

    /**
     * Applies CLAHE (contrast-limited adaptive histogram equalization) on the
     * L channel of LAB color space — boosts local contrast in shadows/dim
     * regions without blowing out already-bright areas, which is what makes
     * it better than a flat brightness/gamma bump for faces walking into a
     * gate from outdoor light or under a single overhead lamp.
     *
     * Returns the ORIGINAL Mat unchanged (no copy, no cost) if the frame is
     * already bright enough per {@link #lowLightMeanBrightnessThreshold}.
     * Otherwise reuses internal scratch Mats — the returned Mat is only
     * valid until the next call to enhanceIfLowLight()/detect().
     */
    private Mat enhanceIfLowLight(Mat frameBgr) {
        if (!lowLightEnhancementEnabled) return frameBgr;

        double brightness = measureBrightness(frameBgr);
        if (brightness >= lowLightMeanBrightnessThreshold) {
            return frameBgr; // already fine — skip the extra work entirely
        }

        Imgproc.cvtColor(frameBgr, labScratch, Imgproc.COLOR_BGR2Lab);

        labChannelsScratch.clear();
        Core.split(labScratch, labChannelsScratch);

        clahe.apply(labChannelsScratch.get(0), labLScratch);
        labLScratch.copyTo(labChannelsScratch.get(0));

        Core.merge(labChannelsScratch, labScratch);
        Imgproc.cvtColor(labScratch, enhancedScratch, Imgproc.COLOR_Lab2BGR);

        return enhancedScratch;
    }

    public int getInputWidth() {
        return inputWidth;
    }

    public int getInputHeight() {
        return inputHeight;
    }

    /**
     * No-op: the OrtSession/OrtEnvironment are owned by your OnnxModel /
     * OnnxRuntimeManager, not by this class. Close those there instead.
     * This method exists only so SCRFDFaceDetector can still be used in a
     * try-with-resources block without accidentally tearing down a session
     * someone else might still be using.
     */
    @Override
    public void close() {
        // intentionally does nothing
    }
}
