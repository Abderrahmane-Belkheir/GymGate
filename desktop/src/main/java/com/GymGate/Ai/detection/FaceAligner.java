package com.GymGate.Ai.detection;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.*;
import org.opencv.imgproc.Imgproc;

/**
 * Turns a detected FaceBox into a crop ready to feed to a recognition model.
 *
 * Two options:
 *  - align(): landmark-based similarity-transform alignment to the standard
 *    112x112 ArcFace template. Use this if your recognition model is
 *    ArcFace/InsightFace-family (very likely, given SCRFD) — it needs pose-
 *    normalized input to give good embeddings, not just a bounding-box crop.
 *  - cropWithMargin(): plain rectangular crop with padding, for models that
 *    don't require landmark alignment, or as a fallback if landmarks are
 *    unavailable (model exported without the kps output).
 */
public final class FaceAligner {

    private FaceAligner() {}

    // Standard 5-point ArcFace reference template for a 112x112 output.
    // (left eye, right eye, nose tip, left mouth corner, right mouth corner)
    private static final Point[] ARC_FACE_TEMPLATE_112 = {
            new Point(38.2946, 51.6963),
            new Point(73.5318, 51.5014),
            new Point(56.0252, 71.7366),
            new Point(41.5493, 92.3655),
            new Point(70.7299, 92.2041)
    };

    /**
     * Landmark-aligned 112x112 face crop, suitable for ArcFace-family
     * recognition models.
     *
     * @throws IllegalArgumentException if the face has no landmarks
     *         (i.e. your SCRFD export doesn't produce a kps output —
     *         use cropWithMargin() instead in that case)
     */
    public static Mat align(Mat frameBgr, FaceBox face) {
        return align(frameBgr, face, 112);
    }

    public static Mat align(Mat frameBgr, FaceBox face, int outputSize) {
        if (!face.hasLandmarks()) {
            throw new IllegalArgumentException(
                    "FaceBox has no landmarks — either re-export SCRFD with the kps output, " +
                    "or use FaceAligner.cropWithMargin() instead.");
        }

        MatOfPoint2f src = new MatOfPoint2f(
                new Point(face.landmarks[0], face.landmarks[1]),
                new Point(face.landmarks[2], face.landmarks[3]),
                new Point(face.landmarks[4], face.landmarks[5]),
                new Point(face.landmarks[6], face.landmarks[7]),
                new Point(face.landmarks[8], face.landmarks[9])
        );

        double scale = outputSize / 112.0;
        Point[] scaledTemplate = new Point[5];
        for (int i = 0; i < 5; i++) {
            scaledTemplate[i] = new Point(
                    ARC_FACE_TEMPLATE_112[i].x * scale,
                    ARC_FACE_TEMPLATE_112[i].y * scale);
        }
        MatOfPoint2f dst = new MatOfPoint2f(scaledTemplate);

        // Similarity transform (rotation + uniform scale + translation) —
        // better conditioned than a raw 3-point affine for this use case.
        Mat transform = Calib3d.estimateAffinePartial2D(src, dst);

        Mat aligned = new Mat();
        if (transform.empty()) {
            // Degenerate landmark configuration (rare) — fall back to a
            // plain crop rather than failing outright.
            src.release();
            dst.release();
            transform.release();
            return cropWithMargin(frameBgr, face, 0.2f, outputSize);
        }

        Imgproc.warpAffine(frameBgr, aligned, transform, new Size(outputSize, outputSize));

        src.release();
        dst.release();
        transform.release();
        return aligned;
    }

    /**
     * Plain rectangular crop with a margin around the detected box, resized
     * to a square. Use when landmarks aren't available.
     *
     * @param marginRatio extra padding around the box as a fraction of its
     *                    size (e.g. 0.2 = 20% padding on each side)
     */
    public static Mat cropWithMargin(Mat frameBgr, FaceBox face, float marginRatio, int outputSize) {
        float w = face.width();
        float h = face.height();
        float mx = w * marginRatio;
        float my = h * marginRatio;

        int x1 = Math.max(0, Math.round(face.x1 - mx));
        int y1 = Math.max(0, Math.round(face.y1 - my));
        int x2 = Math.min(frameBgr.cols(), Math.round(face.x2 + mx));
        int y2 = Math.min(frameBgr.rows(), Math.round(face.y2 + my));

        Rect safeRect = new Rect(x1, y1, x2 - x1, y2 - y1);
        Mat cropped = new Mat(frameBgr, safeRect);

        Mat resized = new Mat();
        Imgproc.resize(cropped, resized, new Size(outputSize, outputSize));
        return resized;
    }
}