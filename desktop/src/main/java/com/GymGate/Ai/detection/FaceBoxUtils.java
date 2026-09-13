package com.GymGate.Ai.detection;

import org.opencv.core.Mat;
import org.opencv.core.Rect;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Common operations you'll want on a List&lt;FaceBox&gt; before handing faces
 * off to recognition. None of this is required — SCRFDFaceDetector.detect()
 * already gives you clean, NMS'd boxes — but a gate scanner usually needs a
 * few extra decisions on top:
 *   - ignore faces too small/far away to recognize reliably
 *   - decide which face to act on if several people are in frame
 *   - crop with a margin, since recognition models expect some context
 *     around the face, not a tight bounding box
 */

/**
 * Common operations you'll want on a List&lt;FaceBox&gt; before handing faces
 * off to recognition. None of this is required — SCRFDFaceDetector.detect()
 * already gives you clean, NMS'd boxes — but a gate scanner usually needs a
 * few extra decisions on top:
 *   - ignore faces too small/far away to recognize reliably
 *   - decide which face to act on if several people are in frame
 *   - crop with a margin, since recognition models expect some context
 *     around the face, not a tight bounding box
 */
public final class FaceBoxUtils {

    private FaceBoxUtils() {}

    /**
     * Drops faces below a minimum pixel size. A tiny box far from the camera
     * usually means the face is too low-resolution for your recognition
     * model to embed reliably — better to ignore it than to feed it in and
     * get a bad/false match.
     *
     * For a gate camera, tune this against your actual approach distance:
     * e.g. if people are only reliably recognizable once their face is
     * ~120px wide in frame, filter anything smaller.
     */
    public static List<FaceBox> filterBySize(List<FaceBox> faces, float minWidthPx, float minHeightPx) {
        return faces.stream()
                .filter(f -> f.width() >= minWidthPx && f.height() >= minHeightPx)
                .collect(Collectors.toList());
    }

    /**
     * Filters on face width only, within an inclusive [minWidthPx, maxWidthPx]
     * range. Unlike {@link #filterBySize}'s min-only floor, this also
     * rejects faces that are TOO large \u2014 i.e. someone standing closer than
     * the intended distance. Meant to be paired with
     * {@link DistanceEstimator} for a physical "stand here" marker: convert
     * your acceptable distance range (cm) to a pixel width range once per
     * frame, then use this to enforce it.
     */
    public static List<FaceBox> filterByWidthRange(List<FaceBox> faces, float minWidthPx, float maxWidthPx) {
        return faces.stream()
                .filter(f -> f.width() >= minWidthPx && f.width() <= maxWidthPx)
                .collect(Collectors.toList());
    }

    /**
     * Drops low-confidence detections beyond what you already threshold in
     * the detector itself — useful if you want a stricter bar specifically
     * for triggering gate access vs. just drawing debug boxes on screen.
     */
    public static List<FaceBox> filterByScore(List<FaceBox> faces, float minScore) {
        return faces.stream()
                .filter(f -> f.score >= minScore)
                .collect(Collectors.toList());
    }

    /**
     * For a single-person gate, you usually only want to act on ONE face per
     * frame even if several are detected (e.g. someone walking past behind
     * the person entering). Picking the LARGEST box is normally the right
     * call — the biggest face is almost always the person closest to the
     * camera/gate, not a bystander in the background.
     */
    public static Optional<FaceBox> largest(List<FaceBox> faces) {
        return faces.stream().max(Comparator.comparingDouble(f -> (double) f.width() * f.height()));
    }

    /** If you'd rather trust the model's own confidence over size. */
    public static Optional<FaceBox> highestScore(List<FaceBox> faces) {
        return faces.stream().max(Comparator.comparingDouble(f -> f.score));
    }

    /**
     * Crops a face out of the frame with a margin around the tight detection
     * box. Recognition/embedding models (ArcFace etc.) are almost always
     * trained on crops that include a bit of forehead/chin/ear, not just the
     * exact SCRFD box — skipping this margin is a common cause of degraded
     * recognition accuracy even when detection itself looks fine.
     *
     * @param marginRatio e.g. 0.2 adds 20% padding on each side
     */
    public static Mat cropWithMargin(Mat frame, FaceBox face, double marginRatio) {
        float marginX = face.width() * (float) marginRatio;
        float marginY = face.height() * (float) marginRatio;

        int x1 = Math.max(0, Math.round(face.x1 - marginX));
        int y1 = Math.max(0, Math.round(face.y1 - marginY));
        int x2 = Math.min(frame.cols(), Math.round(face.x2 + marginX));
        int y2 = Math.min(frame.rows(), Math.round(face.y2 + marginY));

        Rect safeRect = new Rect(x1, y1, x2 - x1, y2 - y1);
        return new Mat(frame, safeRect).clone(); // clone so it outlives the source frame
    }

    /**
     * Quick sanity check before you bother running recognition at all —
     * e.g. skip faces that are heavily cut off at the frame edge (partial
     * profile as someone's walking past, not looking at the gate).
     */
    public static boolean isFullyInFrame(FaceBox face, int frameWidth, int frameHeight, float edgeMarginPx) {
        return face.x1 >= edgeMarginPx
                && face.y1 >= edgeMarginPx
                && face.x2 <= frameWidth - edgeMarginPx
                && face.y2 <= frameHeight - edgeMarginPx;
    }

    /**
     * Intersection-over-union of two face boxes, 0 (no overlap) to 1
     * (identical). Useful as a cheap frame-to-frame continuity check: the
     * same person's face moves smoothly between consecutive frames, so a
     * high IoU against the previous frame's box is a reasonable signal
     * it's still the same person, while a low IoU suggests a different
     * face has taken its place (e.g. someone new stepping up right behind
     * the person who just walked through).
     *
     * This is a heuristic, not a real tracker — in a narrow gate lane
     * where every person's face lands in roughly the same screen region,
     * a fast handoff between two different people can still score a high
     * IoU. It catches the common case (a clean gap or a clearly displaced
     * box) cheaply, without adding a frame of recognition latency; it is
     * not a substitute for re-verifying identity if you need airtight
     * guarantees against a fast queue.
     */
    public static float iou(FaceBox a, FaceBox b) {
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

    /**
     * Head-tilt (roll) in degrees, from the eye-line angle. 0 = eyes level.
     * Returns NaN if the face has no landmarks.
     */
    public static double rollDegrees(FaceBox face) {
        if (!face.hasLandmarks()) return Double.NaN;
        float[] lm = face.landmarks;
        double dx = lm[2] - lm[0]; // rightEyeX - leftEyeX
        double dy = lm[3] - lm[1]; // rightEyeY - leftEyeY
        return Math.toDegrees(Math.atan2(dy, dx));
    }

    /**
     * Cheap yaw ("turned left/right") proxy: ratio of the nose's horizontal
     * distance to each eye. ~1.0 when frontal; drifts toward 0 or infinity
     * as the head turns, since the nose crowds toward whichever eye is
     * closer to the camera.
     * Returns NaN if the face has no landmarks or the landmarks are
     * degenerate (eyes/nose collinear or crossed).
     */
    public static double yawSymmetryRatio(FaceBox face) {
        if (!face.hasLandmarks()) return Double.NaN;
        float[] lm = face.landmarks;
        double leftEyeX = lm[0], rightEyeX = lm[2], noseX = lm[4];
        double leftDist = noseX - leftEyeX;
        double rightDist = rightEyeX - noseX;
        if (leftDist <= 0 || rightDist <= 0) return Double.NaN;
        return leftDist / rightDist;
    }

    /**
     * True if this face is frontal/level enough to trust for recognition.
     * Requires landmarks — a face without them can't be judged (and has no
     * alignedFaceMat), so it's treated as not well-aligned.
     *
     * @param maxRollDeg   max acceptable head tilt, either direction (e.g. 20)
     * @param minYawRatio  min acceptable {@link #yawSymmetryRatio} (e.g. 0.5)
     * @param maxYawRatio  max acceptable {@link #yawSymmetryRatio} (e.g. 2.0)
     */
    public static boolean isWellAligned(FaceBox face, double maxRollDeg, double minYawRatio, double maxYawRatio) {
        if (!face.hasLandmarks()) return false;

        double roll = rollDegrees(face);
        if (Double.isNaN(roll) || Math.abs(roll) > maxRollDeg) return false;

        double yawRatio = yawSymmetryRatio(face);
        if (Double.isNaN(yawRatio) || yawRatio < minYawRatio || yawRatio > maxYawRatio) return false;

        return true;
    }
    /**
     * True if the face's bounding-box center falls within a tolerance
     * window around the frame's center, expressed as a fraction of frame
     * width/height. Complements {@link #isFullyInFrame} — that check only
     * guarantees the box doesn't clip the edges, it says nothing about
     * whether the person is actually standing where the camera/gate
     * expects them to (e.g. someone walking past off to one side, still
     * fully in frame but nowhere near where you'd want a confident
     * recognition to fire).
     *
     * A face that's off-center is often also off-axis relative to the
     * lens (more foreshortening/perspective distortion toward the frame
     * edges), which degrades the embedding on top of just being the wrong
     * spot — gating on this before recognition avoids handing the
     * recognizer a distorted crop that could come back "not found" for a
     * real member simply because they weren't centered yet.
     *
     * @param maxOffsetXRatio how far the face center may drift horizontally
     *                        from frame-center, as a fraction of frame width
     *                        (e.g. 0.25 = must stay within the middle 50% of
     *                        the frame horizontally)
     * @param maxOffsetYRatio same, vertically, as a fraction of frame height
     */
    public static boolean isCentered(FaceBox face, int frameWidth, int frameHeight,
                                      double maxOffsetXRatio, double maxOffsetYRatio) {
        double faceCenterX = (face.x1 + face.x2) / 2.0;
        double faceCenterY = (face.y1 + face.y2) / 2.0;

        double frameCenterX = frameWidth / 2.0;
        double frameCenterY = frameHeight / 2.0;

        double offsetXRatio = Math.abs(faceCenterX - frameCenterX) / frameWidth;
        double offsetYRatio = Math.abs(faceCenterY - frameCenterY) / frameHeight;

        return offsetXRatio <= maxOffsetXRatio && offsetYRatio <= maxOffsetYRatio;
    }
}