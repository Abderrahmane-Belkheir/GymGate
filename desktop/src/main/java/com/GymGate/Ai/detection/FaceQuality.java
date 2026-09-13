package com.GymGate.Ai.detection;

/**
 * Lightweight, landmark-only pose-quality check — no extra model, pure
 * geometry on the same 5-point landmarks FaceAligner already uses.
 *
 * Purpose: a face can pass size/edge-margin filtering and still be a BAD
 * candidate for recognition — tilted, turned to the side, or looking down —
 * which degrades the embedding and causes real members to come back as
 * "not found" purely because of angle, not identity. This class rejects
 * those frames before they ever reach the recognizer, so recognition only
 * fires on faces that are actually looking at the camera.
 *
 * All three checks are cheap arithmetic on the existing 5 landmark points —
 * no extra inference, negligible cost per frame.
 */
public final class FaceQuality {

    private FaceQuality() {}

    // Landmark indices, matching FaceAligner's ordering:
    // [0,1]=leftEye [2,3]=rightEye [4,5]=nose [6,7]=leftMouth [8,9]=rightMouth

    /** In-plane head tilt, in degrees. 0 = perfectly level eyes. */
    public static double computeRollDegrees(FaceBox face) {
        float[] lm = face.landmarks;
        double dx = lm[2] - lm[0];
        double dy = lm[3] - lm[1];
        return Math.toDegrees(Math.atan2(dy, dx));
    }

    /**
     * Left-right turn (yaw), expressed as deviation from 0.5.
     * 0.5 = nose perfectly centered between the eyes (frontal).
     * Moves toward 0 or 1 as the head turns to either side.
     */
    public static double computeYawDeviation(FaceBox face) {
        float[] lm = face.landmarks;
        double leftEyeX = lm[0];
        double rightEyeX = lm[2];
        double noseX = lm[4];

        double eyeSpan = rightEyeX - leftEyeX;
        if (Math.abs(eyeSpan) < 1e-3) {
            return 1.0; // degenerate landmarks — treat as maximally bad
        }
        double yawRatio = (noseX - leftEyeX) / eyeSpan;
        return Math.abs(yawRatio - 0.5);
    }

    /**
     * Up-down tilt (pitch), expressed as deviation from the frontal
     * reference ratio (~0.49, derived from the same ArcFace template
     * FaceAligner aligns to). Moves away from that as the head tilts
     * up or down.
     */
    public static double computePitchDeviation(FaceBox face) {
        float[] lm = face.landmarks;
        double eyeMidY = (lm[1] + lm[3]) / 2.0;
        double noseY = lm[5];
        double mouthMidY = (lm[7] + lm[9]) / 2.0;

        double span = mouthMidY - eyeMidY;
        if (Math.abs(span) < 1e-3) {
            return 1.0; // degenerate landmarks — treat as maximally bad
        }
        double pitchRatio = (noseY - eyeMidY) / span;
        return Math.abs(pitchRatio - 0.49); // 0.49 = frontal reference from the ArcFace template
    }

    /** Bundled result — useful for logging/tuning thresholds against real footage. */
    public record Result(double rollDegrees, double yawDeviation, double pitchDeviation, boolean acceptable) {

        public String reason() {
            if (acceptable) return "OK";
            StringBuilder sb = new StringBuilder("Rejected: ");
            if (Math.abs(rollDegrees) > 0) sb.append("roll=").append(String.format("%.1f", rollDegrees)).append("deg ");
            sb.append("yawDev=").append(String.format("%.2f", yawDeviation)).append(" ");
            sb.append("pitchDev=").append(String.format("%.2f", pitchDeviation));
            return sb.toString();
        }
    }

    public static Result assess(FaceBox face, double maxRollDegrees, double maxYawDeviation, double maxPitchDeviation) {
        if (!face.hasLandmarks()) {
            return new Result(0, 1, 1, false);
        }

        double roll = computeRollDegrees(face);
        double yaw = computeYawDeviation(face);
        double pitch = computePitchDeviation(face);

        boolean acceptable = Math.abs(roll) <= maxRollDegrees
                && yaw <= maxYawDeviation
                && pitch <= maxPitchDeviation;

        return new Result(roll, yaw, pitch, acceptable);
    }
}
