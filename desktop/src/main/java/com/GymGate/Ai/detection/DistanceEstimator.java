package com.GymGate.Ai.detection;

/**
 * Converts between a person's real-world distance from the camera and the
 * expected on-screen face width in pixels, using simple pinhole-camera
 * geometry: visibleWidthAtDistance = 2 * distance * tan(hFOV / 2).
 *
 * This exists so distance-based gating (e.g. "only recognize someone
 * standing on the floor marker, ~80cm out") can be configured in real-world
 * units \u2014 centimeters \u2014 instead of hand-tuned pixel thresholds that
 * silently go stale the moment you change camera, resolution, or mounting.
 *
 * The math is only as good as its two inputs, both of which vary in
 * reality (not everyone's face is the same width, no lens is perfectly
 * rectilinear at the edges) \u2014 treat this as a solid estimate to build a
 * reasonable acceptance window around, not a precise ruler. Validate against
 * real measurements at your actual floor marker before trusting the
 * defaults; see {@link FaceDetectionService#estimateDistanceCm}.
 */
public class DistanceEstimator {

    /** Default capture device's spec'd horizontal FOV \u2014 override via the constructor if using a different camera. */
    public static final double DEFAULT_HORIZONTAL_FOV_DEGREES = 70.0;

    /** Average adult face width, in cm \u2014 reasonable population average for this purpose. */
    public static final double DEFAULT_AVERAGE_FACE_WIDTH_CM = 15.0;

    private final double horizontalFovRadians;
    private final double averageFaceWidthCm;

    public DistanceEstimator() {
        this(DEFAULT_HORIZONTAL_FOV_DEGREES, DEFAULT_AVERAGE_FACE_WIDTH_CM);
    }

    public DistanceEstimator(double horizontalFovDegrees, double averageFaceWidthCm) {
        this.horizontalFovRadians = Math.toRadians(horizontalFovDegrees);
        this.averageFaceWidthCm = averageFaceWidthCm;
    }

    /** Expected face width in pixels for someone standing distanceCm from the camera, at this frame width. */
    public double expectedFaceWidthPx(double distanceCm, int frameWidthPx) {
        double visibleWidthCmAtDistance = 2.0 * distanceCm * Math.tan(horizontalFovRadians / 2.0);
        return (averageFaceWidthCm / visibleWidthCmAtDistance) * frameWidthPx;
    }

    /** Inverse: estimated distance in cm for an observed face width in pixels, at this frame width. */
    public double estimateDistanceCm(double faceWidthPx, int frameWidthPx) {
        double visibleWidthCmAtDistance = (averageFaceWidthCm * frameWidthPx) / faceWidthPx;
        return visibleWidthCmAtDistance / (2.0 * Math.tan(horizontalFovRadians / 2.0));
    }
}
