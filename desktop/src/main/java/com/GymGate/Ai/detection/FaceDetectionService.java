package com.GymGate.Ai.detection;


import ai.onnxruntime.OrtException;
import com.GymGate.Ai.models.ModelManager;
import org.opencv.core.Mat;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * High-level entry point for the gate's face pipeline.
 *
 * Wraps SCRFDFaceDetector + the filtering/selection/cropping logic from
 * FaceBoxUtils into a single call: give it a live camera frame, get back
 * (at most) one face crop that's actually worth running recognition on —
 * or nothing, if there's no usable face in this frame.
 *
 * GATING PHILOSOPHY (changed): the gates here are a RECOGNIZABILITY filter,
 * not a "stand exactly here" enforcement. Anything that would still give
 * ArcFace a usable embedding is let through; only genuinely unusable faces
 * (too far to resolve, clipped by the frame edge, turned away, or a
 * low-confidence non-face detection) are dropped. The job of deciding
 * *identity* — and of deciding when there's been enough evidence to say
 * "not a member" — belongs to FaceProcessor downstream, which is patient
 * about it. That split is what keeps this from being a pipeline that only
 * works on one exact spot on the floor.
 */
public class FaceDetectionService {

    public enum SelectionStrategy { LARGEST, HIGHEST_SCORE }

    /**
     * One face that is present at the gate, plus whether it is currently in a
     * good enough pose to be worth embedding.
     *
     * <p>The split matters for tracking. A person who glances sideways for a
     * frame or two is still THE SAME PERSON standing there — dropping them from
     * the frame's face list entirely (which is what the old single-face path
     * did) makes their track look like it ended, and the next frame looks like
     * a brand new arrival. So the presence gates (confidence, distance/size,
     * fully in frame, roughly centred) decide whether a face is listed at all,
     * while the pose gate only decides whether recognition may spend an
     * inference on it this frame. See {@link FaceDetectionService#detectCandidates}.
     */
    public record Candidate(FaceBox box, boolean recognizable) {}

    private final SCRFDFaceDetector detector;

    // ---- gate-appropriate filtering, tune these against your real camera ----
    private float minFaceWidthPx = 90f;
    private float minFaceHeightPx = 90f;
    private float edgeMarginPx = 5f;
    private double cropMarginRatio = 0.2;
    private SelectionStrategy selectionStrategy = SelectionStrategy.LARGEST;

    // ---- false-detection guard ----
    // The detector's own 0.5 score threshold is tuned for "is this a face at
    // all" (fine for drawing debug boxes). For anything that can trigger a
    // gate decision we want a stricter bar, because a weak detection is
    // exactly what a printed photo on a wall, a reflection, or a face-like
    // texture produces — and a bad crop fed to ArcFace is what generates a
    // confident-looking wrong answer. Cheap, and it removes a whole class of
    // spurious "not found" notifications caused by things that were never a
    // person.
    private float minScoreForRecognition = 0.62f;

    // ---- distance gating ----
    // WHAT CHANGED AND WHY: this used to be a narrow 60-100cm window, which
    // in practice meant a member was only ever seen while standing almost
    // exactly on one spot — a step forward or back and the pipeline went
    // completely blind. It's now a wide *recognizability* window: reject
    // faces so far away there isn't enough pixel detail for a trustworthy
    // embedding, and faces so close they're clipping/fisheyed, and accept
    // everything in between.
    //
    // The estimate is also deliberately given slack (see
    // distanceToleranceRatio): DistanceEstimator assumes a 15cm average
    // face width, but real faces vary by well over 10% either way, so a
    // hard pixel edge derived from an average was rejecting narrow-faced
    // people at distances where wide-faced people were accepted.
    private boolean distanceGatingEnabled = true;
    private DistanceEstimator distanceEstimator = new DistanceEstimator();
    private double minStandingDistanceCm = 40.0;   // closer than this = clipped/distorted
    private double maxStandingDistanceCm = 160.0;  // farther than this = too little detail

    /** Extra slack on both ends of the converted pixel range, absorbing real face-width variation. */
    private double distanceToleranceRatio = 0.35;

    /**
     * Hard floor on face width regardless of what the distance math says —
     * below this there simply isn't enough detail for ArcFace, whatever the
     * geometry claims.
     */
    private float minRecognizableFaceWidthPx = 80f;

    // ---- centering gate ----
    // WHAT CHANGED AND WHY: was 0.25/0.30 (face had to sit in the middle
    // 50%/60% of the frame), then 0.40/0.40 (still rejected registration/
    // update shots for not being dead-center enough — too aggressive for a
    // one-time deliberate capture where the subject is plainly at the
    // camera). Now wide enough to accept a person standing almost anywhere
    // in frame, and only excludes faces hugging the very edge — which are
    // the ones that are both heavily lens-distorted AND usually bystanders
    // rather than the person at the gate. Set to 0.5/0.5 to disable the
    // check entirely.
    private double maxCenterOffsetXRatio = 0.46;
    private double maxCenterOffsetYRatio = 0.46;

    // ---- pose-quality gating ----
    // WHAT CHANGED AND WHY: was 20deg / 0.18 / 0.18, which is close to
    // "dead frontal or nothing" and is the single biggest reason a real
    // member could stand there looking at the camera and never get seen.
    // Widened to cover natural head movement while still rejecting true
    // profile / looking-away / heavily-tilted frames, which are the ones
    // that actually poison the embedding.
    private double maxRollDegrees = 30.0;
    private double maxYawDeviation = 0.32;
    private double maxPitchDeviation = 0.32;
    private boolean logQualityRejections = false;

    // ---- low-light correction for the recognition crop ----
    // SCRFDFaceDetector enhances the whole frame only to *locate* faces; the
    // crop handed to ArcFace is warped from the raw frame, so a dim or backlit
    // face would still be embedded dark. Re-applying the same CLAHE step on
    // the aligned crop fixes that. detectForEnrollment() runs this exact path,
    // so gallery and probe embeddings stay consistent.
    private boolean lowLightCropEnhancementEnabled = true;
    private double lowLightMeanBrightnessThreshold = 90.0;
    private final LowLightEnhancer cropEnhancer = new LowLightEnhancer();

    // ---- focus measurement ----
    // Measured on the finished crop (after low-light correction, i.e. exactly
    // what ArcFace sees) and recorded to recognition-log.csv. Not a gate yet —
    // the rejection floor should come from this install's own logged
    // sharpness/score correlation, not from a borrowed constant.
    private final FaceSharpness sharpnessMeter = new FaceSharpness();

    public FaceDetectionService(float detectorScoreThreshold, float nmsThreshold) throws OrtException {
        this.detector = new SCRFDFaceDetector(ModelManager.getInstance().detector().session(), detectorScoreThreshold, nmsThreshold,640, 640  );
    }

    /** Convenience constructor using the same detection defaults discussed earlier (0.5 / 0.4). */
    public FaceDetectionService() throws OrtException {
        this( 0.5f, 0.4f);
    }

    /**
     * Runs detection on a live frame and returns the single face crop worth
     * handing to recognition, if any.
     *
     * Returns Optional.empty() when: no face detected, every detected face
     * is unrecognizable (too far, clipped, turned away, or a low-confidence
     * detection), or the frame itself is empty. Callers don't need to know
     * why — just: is there something to recognize right now, yes or no.
     * Crucially, empty here means "nothing to say yet", NOT "not a member";
     * only FaceProcessor decides that, and only after collecting enough
     * evidence.
     */
    public Optional<FaceDetectionResult> detectForRecognition(Mat frameBgr) throws OrtException {
        List<Candidate> candidates = detectCandidates(frameBgr);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        // Select FIRST, then require the winner to be well-posed — deliberately
        // not "the best-posed face in the frame". Enrollment runs through here,
        // and a shot taken while the subject is mid-turn must produce nothing
        // rather than quietly enrolling whoever is standing behind them.
        List<FaceBox> present = candidates.stream().map(Candidate::box).collect(Collectors.toList());
        Optional<FaceBox> chosen = selectionStrategy == SelectionStrategy.LARGEST
                ? FaceBoxUtils.largest(present)
                : FaceBoxUtils.highestScore(present);
        if (chosen.isEmpty()) {
            return Optional.empty();
        }

        FaceBox target = chosen.get();
        boolean recognizable = candidates.stream()
                .anyMatch(c -> c.box() == target && c.recognizable());
        if (!recognizable) {
            return Optional.empty();
        }

        return buildRecognitionResult(frameBgr, target);
    }

    /**
     * Every face in this frame that is plausibly a person at the gate, each
     * flagged with whether its pose is good enough to embed right now.
     *
     * <p>This is the entry point for the multi-person path: it does NOT pick a
     * winner. Choosing which face to spend an inference on — and keeping
     * per-person evidence separate so two people standing at similar distance
     * don't overwrite each other's attempts — is
     * {@link com.GymGate.Ai.FaceProcessor}'s job, because that decision needs
     * memory across frames and this class deliberately has none.
     *
     * <p>Returns boxes only, no {@code Mat}s: nothing here needs releasing, and
     * the (comparatively expensive) crop/align/enhance work only happens for
     * the single face that is actually going to be recognised — see
     * {@link #buildRecognitionResult}.
     */
    public List<Candidate> detectCandidates(Mat frameBgr) throws OrtException {
        if (frameBgr == null || frameBgr.empty()) {
            return List.of();
        }

        List<FaceBox> faces = detector.detect(frameBgr);
        if (faces.isEmpty()) {
            return List.of();
        }

        // Confident detections only — see minScoreForRecognition.
        faces = FaceBoxUtils.filterByScore(faces, minScoreForRecognition);
        if (faces.isEmpty()) {
            return List.of();
        }

        List<FaceBox> sized;
        if (distanceGatingEnabled) {
            double slackLow = 1.0 - distanceToleranceRatio;
            double slackHigh = 1.0 + distanceToleranceRatio;

            float minWidthPx = (float) (distanceEstimator.expectedFaceWidthPx(maxStandingDistanceCm, frameBgr.cols()) * slackLow);
            float maxWidthPx = (float) (distanceEstimator.expectedFaceWidthPx(minStandingDistanceCm, frameBgr.cols()) * slackHigh);

            // Never go below the absolute detail floor, however generous the
            // geometry gets.
            minWidthPx = Math.max(minWidthPx, minRecognizableFaceWidthPx);

            sized = FaceBoxUtils.filterByWidthRange(faces, minWidthPx, maxWidthPx);
        } else {
            sized = FaceBoxUtils.filterBySize(faces, minFaceWidthPx, minFaceHeightPx);
        }

        List<Candidate> candidates = new ArrayList<>(sized.size());
        for (FaceBox face : sized) {
            if (!FaceBoxUtils.isFullyInFrame(face, frameBgr.cols(), frameBgr.rows(), edgeMarginPx)) {
                continue;
            }
            if (!FaceBoxUtils.isCentered(face, frameBgr.cols(), frameBgr.rows(),
                    maxCenterOffsetXRatio, maxCenterOffsetYRatio)) {
                continue;
            }

            // ---- pose-quality gate ----
            // Without landmarks we cannot verify alignment quality at all, so a
            // face this pipeline can't confirm is "looking at the camera" is
            // never handed to recognition: a bad angle produces a bad embedding
            // and a false "member not found" for a real, enrolled member.
            // Unlike the gates above, failing this does NOT drop the face from
            // the list — the person is still standing there, and their track
            // must survive a brief glance away (see Candidate).
            FaceQuality.Result quality =
                    FaceQuality.assess(face, maxRollDegrees, maxYawDeviation, maxPitchDeviation);
            if (!quality.acceptable() && logQualityRejections) {
                System.out.println("Face rejected by quality gate: " + quality.reason());
            }
            candidates.add(new Candidate(face, quality.acceptable()));
        }

        return candidates;
    }

    /**
     * Does the actual crop/align/low-light work for ONE chosen face — the
     * expensive half of what {@link #detectForRecognition} used to do inline.
     *
     * <p>The returned {@link FaceDetectionResult} owns its {@code Mat}s and the
     * caller must {@link FaceDetectionResult#release()} them.
     *
     * <p>Empty only if the face carries no landmarks, which cannot happen for a
     * candidate flagged {@code recognizable} (the pose gate rejects a
     * landmark-less face outright) — it is guarded here so a caller that picks
     * its own {@link FaceBox} can't trip {@link FaceAligner}'s exception.
     */
    public Optional<FaceDetectionResult> buildRecognitionResult(Mat frameBgr, FaceBox target) {
        if (frameBgr == null || frameBgr.empty() || target == null || !target.hasLandmarks()) {
            return Optional.empty();
        }

        Mat faceCrop = FaceBoxUtils.cropWithMargin(frameBgr, target, cropMarginRatio);
        Mat alignedFaceCrop = FaceAligner.align(frameBgr, target);

        // Lift a dim/backlit face to usable contrast before it reaches ArcFace.
        // Measured on the crop itself, so it also catches a dark face in an
        // otherwise bright scene (which the frame-level check would miss).
        if (lowLightCropEnhancementEnabled) {
            Mat lit = cropEnhancer.enhanceIfLowLight(alignedFaceCrop, lowLightMeanBrightnessThreshold);
            if (lit != alignedFaceCrop) {
                lit.copyTo(alignedFaceCrop); // enhancer's buffer is scratch — keep our owned Mat
            }
        }

        double sharpness = sharpnessMeter.measure(alignedFaceCrop);

        return Optional.of(new FaceDetectionResult(target, faceCrop, alignedFaceCrop, sharpness));
    }

    /**
     * Escape hatch for when you need ALL detected faces — e.g. drawing debug
     * overlays for every person in frame, or a future multi-person gate.
     * Most gate logic should just use detectForRecognition() instead.
     */
    public List<FaceBox> detectAll(Mat frameBgr) throws OrtException {
        return detector.detect(frameBgr);
    }

    /**
     * Registration/enrollment capture should call THIS, not roll its own
     * capture logic. It's currently a straight pass-through to
     * {@link #detectForRecognition} on purpose: the whole point is that
     * enrollment and recognition-time faces go through the exact same
     * distance/pose/centering/size gates. If a photo is good enough to
     * enroll, it must be good enough that the SAME gate would also accept
     * it at recognition time \u2014 otherwise you get exactly the failure mode
     * of a real member permanently mismatching because their one enrollment
     * photo was captured under looser conditions than the gate ever
     * enforces again. Kept as a separate named method (rather than just
     * telling callers to use detectForRecognition directly) so enrollment
     * can diverge later \u2014 e.g. stricter pose thresholds, since it's a
     * one-time deliberate capture \u2014 without a confusing rename at that point.
     *
     * Note that the widened gates make this MORE important, not less: since
     * recognition now accepts a range of distances and angles, enrollment
     * going through the same widened gates means the gallery ends up
     * containing the same variety of poses the gate will actually see.
     */
    public Optional<FaceDetectionResult> detectForEnrollment(Mat frameBgr) throws OrtException {
        return detectForRecognition(frameBgr);
    }

    // ---- tuning knobs ----

    public void setMinFaceSize(float widthPx, float heightPx) {
        this.minFaceWidthPx = widthPx;
        this.minFaceHeightPx = heightPx;
    }

    public void setEdgeMargin(float edgeMarginPx) {
        this.edgeMarginPx = edgeMarginPx;
    }

    public void setCropMarginRatio(double cropMarginRatio) {
        this.cropMarginRatio = cropMarginRatio;
    }

    public void setSelectionStrategy(SelectionStrategy strategy) {
        this.selectionStrategy = strategy;
    }

    /**
     * Minimum detector confidence for a face to be eligible for recognition
     * (default 0.62, stricter than the detector's own 0.5 threshold).
     * Raise it if non-faces or photos are still triggering attempts; lower
     * it if real people at the edge of the lit area are being ignored.
     */
    public void setMinScoreForRecognition(float minScoreForRecognition) {
        this.minScoreForRecognition = minScoreForRecognition;
    }

    /**
     * Tunes how far off-center a face may be and still fire recognition.
     * Both values are fractions of frame width/height (0-0.5): lower means
     * stricter (face must be closer to dead-center), 0.5 effectively
     * disables the check (whole frame is "centered"). Defaults (0.46, 0.46)
     * intentionally accept a person standing almost anywhere plausible and
     * only exclude faces pressed against the frame border.
     */
    public void setCenterTolerance(double maxOffsetXRatio, double maxOffsetYRatio) {
        this.maxCenterOffsetXRatio = maxOffsetXRatio;
        this.maxCenterOffsetYRatio = maxOffsetYRatio;
    }

    /**
     * Sets the acceptable real-world distance window from the camera, in
     * centimeters. Defaults (40-160cm) are a wide recognizability window,
     * not a "stand here" marker — widen or narrow only if you genuinely
     * want to change which distances are physically usable.
     * Requires {@link #setDistanceGatingEnabled} to stay true (the default).
     *
     * These are starting points, not measured truth \u2014 validate against
     * your actual camera using {@link #estimateDistanceCm} before
     * trusting them for a live gate.
     */
    public void setStandingDistanceRange(double minDistanceCm, double maxDistanceCm) {
        this.minStandingDistanceCm = minDistanceCm;
        this.maxStandingDistanceCm = maxDistanceCm;
    }

    /**
     * Slack applied to both ends of the distance-derived pixel range
     * (default 0.35 = 35%), covering the fact that DistanceEstimator works
     * off an average face width while real faces vary. Lower it only if you
     * specifically need tight distance enforcement.
     */
    public void setDistanceToleranceRatio(double distanceToleranceRatio) {
        this.distanceToleranceRatio = distanceToleranceRatio;
    }

    /**
     * Absolute minimum face width in pixels for recognition, applied on top
     * of distance gating (default 80px). This is the "is there enough detail
     * to embed" floor.
     */
    public void setMinRecognizableFaceWidthPx(float minRecognizableFaceWidthPx) {
        this.minRecognizableFaceWidthPx = minRecognizableFaceWidthPx;
    }

    /**
     * Overrides the camera's horizontal field of view used for distance
     * estimation (default: 70deg, the default capture device's spec). Change this if
     * you switch cameras, or provide a measured value if the spec'd FOV
     * doesn't match what you observe in testing.
     */
    public void setCameraHorizontalFov(double horizontalFovDegrees) {
        this.distanceEstimator = new DistanceEstimator(horizontalFovDegrees, DistanceEstimator.DEFAULT_AVERAGE_FACE_WIDTH_CM);
    }

    /**
     * Switches between distance-based gating (default, uses
     * {@link #setStandingDistanceRange}) and the older flat pixel-size
     * floor ({@link #setMinFaceSize}). Turn this off if you don't have a
     * fixed standing spot and just want a simple minimum-size floor.
     */
    public void setDistanceGatingEnabled(boolean enabled) {
        this.distanceGatingEnabled = enabled;
    }

    /**
     * Estimates how far (in cm) a detected face's owner is standing from
     * the camera, based on its width in pixels. Use this to calibrate
     * {@link #setStandingDistanceRange} against reality: have someone stand
     * exactly on your floor marker, log this value for their detected face,
     * and adjust the FOV/range settings until it reads close to your actual
     * measured marker distance.
     */
    public double estimateDistanceCm(FaceBox face, int frameWidthPx) {
        return distanceEstimator.estimateDistanceCm(face.width(), frameWidthPx);
    }

    /**
     * Tunes how strict the pose-quality gate is. Lower values require a
     * more frontal, level face before recognition fires; higher values are
     * more permissive. Defaults (30deg roll, 0.32 yaw/pitch deviation) accept
     * natural head movement while still rejecting profile/looking-away
     * frames — loosen further only if legitimate members are still being
     * ignored, tighten if clearly-turned-away faces are producing attempts.
     */
    public void setPoseQualityThresholds(double maxRollDegrees, double maxYawDeviation, double maxPitchDeviation) {
        this.maxRollDegrees = maxRollDegrees;
        this.maxYawDeviation = maxYawDeviation;
        this.maxPitchDeviation = maxPitchDeviation;
    }

    /** Prints why each rejected face failed the pose-quality gate — handy while tuning thresholds. */
    public void setLogQualityRejections(boolean enabled) {
        this.logQualityRejections = enabled;
    }

    /** Exposes the raw quality assessment for a face, e.g. for a debug overlay or manual tuning. */
    public FaceQuality.Result assessQuality(FaceBox face) {
        return FaceQuality.assess(face, maxRollDegrees, maxYawDeviation, maxPitchDeviation);
    }

    /**
     * Controls low-light enhancement for BOTH stages: the detector's
     * frame-level pass (to locate faces) and the aligned-crop pass here (so
     * ArcFace embeds a face with usable contrast). Frames/crops at or above
     * {@code meanBrightnessThreshold} pass through untouched.
     */
    public void setLowLightEnhancement(boolean enabled, double meanBrightnessThreshold) {
        this.lowLightCropEnhancementEnabled = enabled;
        this.lowLightMeanBrightnessThreshold = meanBrightnessThreshold;
        detector.setLowLightEnhancement(enabled, meanBrightnessThreshold);
    }

    public double measureBrightness(Mat frameBgr) {
        return detector.measureBrightness(frameBgr);
    }


}
