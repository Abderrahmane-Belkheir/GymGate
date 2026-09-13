package com.GymGate.Ai.detection;

import org.opencv.core.Mat;

public class FaceDetectionResult {

    public final FaceBox faceBox;
    public final Mat faceMat;

    /** 112x112 ArcFace-ready aligned crop. Null if the model has no landmarks. */
    public final Mat alignedFaceMat;

    /**
     * Variance-of-Laplacian focus score of {@link #alignedFaceMat} — see
     * {@link FaceSharpness}. Recorded for diagnostics; not currently a gate.
     */
    public final double sharpness;

    public FaceDetectionResult(FaceBox faceBox, Mat faceMat, Mat alignedFaceMat) {
        this(faceBox, faceMat, alignedFaceMat, 0.0);
    }

    public FaceDetectionResult(FaceBox faceBox, Mat faceMat, Mat alignedFaceMat, double sharpness) {
        this.faceBox = faceBox;
        this.faceMat = faceMat;
        this.alignedFaceMat = alignedFaceMat;
        this.sharpness = sharpness;
    }

    public void release() {
        if (faceMat != null) {
            faceMat.release();
        }
        if (alignedFaceMat != null) {
            alignedFaceMat.release();
        }
    }
}
