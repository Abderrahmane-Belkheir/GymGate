package com.GymGate.Ai.detection;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDouble;
import org.opencv.imgproc.Imgproc;

/**
 * Variance-of-Laplacian focus measure for an aligned face crop.
 *
 * <p>Motion blur is the main thing that quietly degrades an embedding at a
 * gate: a blurred face loses the high-frequency detail the recognizer keys on,
 * which pulls its embedding toward the "average face" direction. That both
 * lowers the genuine score AND raises the score against everyone else — the
 * worst combination for a threshold-plus-margin decision. This measures it in
 * about a tenth of a millisecond on a 112x112 crop.
 *
 * <p>The value is an unnormalised variance in 8-bit intensity units, so it is
 * only meaningful relative to other measurements from the SAME camera and crop
 * size. It is currently recorded to recognition-log.csv and NOT used as a gate:
 * log first, then pick a rejection floor from the observed correlation between
 * sharpness and score, rather than importing a constant that was fitted to
 * somebody else's camera.
 *
 * <p>NOT thread-safe: reuses internal Mats across calls, matching the rest of
 * the pipeline. Callers hold FaceProcessor's inference lock.
 */
public final class FaceSharpness {

    private final Mat grayScratch = new Mat();
    private final Mat laplacianScratch = new Mat();
    private final MatOfDouble meanScratch = new MatOfDouble();
    private final MatOfDouble stdDevScratch = new MatOfDouble();

    /**
     * Focus score for a BGR (or already-grayscale) crop. Higher is sharper.
     * Returns 0 for an empty input rather than throwing — a logging/diagnostic
     * measure must never be able to break the gate.
     */
    public double measure(Mat bgr) {
        if (bgr == null || bgr.empty()) {
            return 0.0;
        }
        try {
            Mat gray;
            if (bgr.channels() == 1) {
                gray = bgr;
            } else {
                Imgproc.cvtColor(bgr, grayScratch, Imgproc.COLOR_BGR2GRAY);
                gray = grayScratch;
            }

            Imgproc.Laplacian(gray, laplacianScratch, CvType.CV_64F);
            Core.meanStdDev(laplacianScratch, meanScratch, stdDevScratch);

            double sd = stdDevScratch.get(0, 0)[0];
            return sd * sd;
        } catch (Throwable t) {
            return 0.0;
        }
    }
}
