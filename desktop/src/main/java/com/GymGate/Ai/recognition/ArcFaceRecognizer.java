package com.GymGate.Ai.recognition;

import ai.onnxruntime.*;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.Map;


public class ArcFaceRecognizer {

    private static final float MEAN = 127.5f;
    private static final float STD = 127.5f;

    // w600k_r50's standard export size — used only as a fallback when the
    // ONNX graph has dynamic input axes (shape reports -1).
    private static final int DEFAULT_INPUT_SIZE = 112;

    private final OrtEnvironment env;
    private final OrtSession session;
    private final String inputName;

    private final int inputWidth;
    private final int inputHeight;
    private final int embeddingSize;

    // Reused OpenCV Mat for the resize step only — everything past that is
    // done directly on raw bytes (see preprocess()), so we no longer need
    // a separate "rgb"/float Mat at all.
    private final Mat resized = new Mat();

    // Reused buffers
    private final byte[] bgrBytes;
    private final float[] chw;

    // Set to true to print a per-stage timing breakdown (preprocess / tensor build / inference)
    private boolean debugTiming = false;

    public ArcFaceRecognizer(OrtSession session) throws OrtException {
        this.session = session;
        this.env = OrtEnvironment.getEnvironment();

        Map<String, NodeInfo> inputInfo = session.getInputInfo();
        inputName = inputInfo.keySet().iterator().next();

        TensorInfo inputTensorInfo = (TensorInfo) inputInfo.get(inputName).getInfo();
        long[] shape = inputTensorInfo.getShape();

        int h = (int) shape[2];
        int w = (int) shape[3];
        if (h <= 0 || w <= 0) {
            // Dynamic axes exported without a fixed size — fall back to
            // w600k_r50's standard 112x112. Override by passing an
            // explicit size if your export differs.
            h = DEFAULT_INPUT_SIZE;
            w = DEFAULT_INPUT_SIZE;
        }
        this.inputHeight = h;
        this.inputWidth = w;

        bgrBytes = new byte[inputHeight * inputWidth * 3];
        chw = new float[inputHeight * inputWidth * 3];

        Map<String, NodeInfo> outputInfo = session.getOutputInfo();
        TensorInfo outputTensorInfo = (TensorInfo) outputInfo.values().iterator().next().getInfo();
        long[] outShape = outputTensorInfo.getShape();

        int resolvedEmbeddingSize = outShape.length > 1 ? (int) outShape[1] : -1;
        if (resolvedEmbeddingSize <= 0) {
            throw new IllegalStateException(
                    "Could not resolve a valid embedding size from the model's output shape: "
                            + java.util.Arrays.toString(outShape)
                            + ". Check the ONNX export's output tensor definition.");
        }
        this.embeddingSize = resolvedEmbeddingSize;
    }

    public float[] getEmbedding(Mat alignedFace) throws OrtException {
        long t0 = debugTiming ? System.nanoTime() : 0;
        preprocess(alignedFace);
        long t1 = debugTiming ? System.nanoTime() : 0;

        OnnxTensor tensor = OnnxTensor.createTensor(
                env, FloatBuffer.wrap(chw), new long[]{1, 3, inputHeight, inputWidth});
        long t2 = debugTiming ? System.nanoTime() : 0;

        float[] embedding;
        try (OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
            OnnxTensor output = (OnnxTensor) result.get(0);
            FloatBuffer buffer = output.getFloatBuffer();
            embedding = new float[embeddingSize];
            buffer.get(embedding);
        } finally {
            tensor.close();
        }

        if (debugTiming) {
            long t3 = System.nanoTime();
            System.out.printf(
                    "ArcFace: preprocess=%.2fms tensor=%.2fms inference=%.2fms total=%.2fms%n",
                    (t1 - t0) / 1_000_000.0,
                    (t2 - t1) / 1_000_000.0,
                    (t3 - t2) / 1_000_000.0,
                    (t3 - t0) / 1_000_000.0);
        }

        l2Normalize(embedding);
        return embedding;
    }

    /**
     * Previously: resize -> cvtColor(BGR->RGB) -> convertTo(32F) -> get() ->
     * a separate HWC->CHW loop. That's three full-image native passes plus
     * a copy before any real work starts. Now: resize (still native, still
     * needed) -> one Mat#get() to pull raw BGR bytes -> one Java loop that
     * does the channel swap, normalization, and HWC->CHW reorder together.
     */
    private void preprocess(Mat bgr) {
        Mat source;
        if (bgr.cols() != inputWidth || bgr.rows() != inputHeight) {
            Imgproc.resize(bgr, resized, new Size(inputWidth, inputHeight));
            source = resized;
        } else {
            source = bgr;
        }

        source.get(0, 0, bgrBytes);

        int plane = inputHeight * inputWidth;
        for (int i = 0; i < plane; i++) {
            int base = i * 3;
            float b = (bgrBytes[base] & 0xFF);
            float g = (bgrBytes[base + 1] & 0xFF);
            float r = (bgrBytes[base + 2] & 0xFF);
            chw[i] = (r - MEAN) / STD;               // R plane
            chw[plane + i] = (g - MEAN) / STD;        // G plane
            chw[2 * plane + i] = (b - MEAN) / STD;    // B plane
        }
    }

    private static void l2Normalize(float[] v) {
        double norm = 0;
        for (float f : v) norm += f * f;
        norm = Math.sqrt(norm);
        if (norm == 0) return;
        for (int i = 0; i < v.length; i++) v[i] /= (float) norm;
    }

    /** Both vectors are expected to already be L2-normalized (getEmbedding() guarantees this), so dot product == cosine similarity. */
    public float cosineSimilarity(float[] a, float[] b) {
        float dot = 0f;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot;
    }

    /** Enable to print per-stage timing on every getEmbedding() call — useful for chasing latency variance. */
    public void setDebugTiming(boolean enabled) {
        this.debugTiming = enabled;
    }

    public int getInputWidth() { return inputWidth; }
    public int getInputHeight() { return inputHeight; }
    public int getEmbeddingSize() { return embeddingSize; }
}
