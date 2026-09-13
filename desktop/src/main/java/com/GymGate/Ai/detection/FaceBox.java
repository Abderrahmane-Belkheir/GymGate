package com.GymGate.Ai.detection;

import org.opencv.core.Rect;

/**
 * Represents a single detected face.
 *
 * Coordinates are in the ORIGINAL frame's pixel space (not the resized
 * model-input space) — the detector un-scales everything for you before
 * this object is returned.
 */
public class FaceBox {

    public final float x1, y1, x2, y2;
    public final float score;

    /**
     * 5-point landmarks: [leftEyeX, leftEyeY, rightEyeX, rightEyeY,
     * noseX, noseY, leftMouthX, leftMouthY, rightMouthX, rightMouthY]
     * Will be null if the loaded ONNX model doesn't export keypoints.
     */
    public final float[] landmarks;

    public FaceBox(float x1, float y1, float x2, float y2, float score, float[] landmarks) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
        this.score = score;
        this.landmarks = landmarks;
    }

    public float width() {
        return x2 - x1;
    }

    public float height() {
        return y2 - y1;
    }

    /** Convenience accessor for drawing / cropping with OpenCV. */
    public Rect toRect() {
        return new Rect((int) x1, (int) y1, (int) Math.round(width()), (int) Math.round(height()));
    }

    public boolean hasLandmarks() {
        return landmarks != null;
    }

    @Override
    public String toString() {
        return String.format("FaceBox[x1=%.1f, y1=%.1f, x2=%.1f, y2=%.1f, score=%.3f, landmarks=%s]",
                x1, y1, x2, y2, score, hasLandmarks() ? "yes" : "no");
    }
}