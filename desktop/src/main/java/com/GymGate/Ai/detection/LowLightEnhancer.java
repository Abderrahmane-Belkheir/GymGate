package com.GymGate.Ai.detection;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgproc.CLAHE;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;

/**
 * CLAHE-based low-light correction, factored out so both stages of the face
 * pipeline can apply the same treatment:
 *   - the detector, on the whole frame, so a dim face is still *found*
 *   - the recognition path, on the aligned crop, so ArcFace embeds a face
 *     with usable contrast instead of one warped straight from a dark frame
 *
 * CLAHE runs on the L channel in LAB space: it lifts shadow detail without
 * blowing out already-bright regions, which is what makes it better than a
 * flat brightness/gamma bump for faces coming in from outdoor light or under
 * a single overhead lamp.
 *
 * NOT thread-safe: it reuses internal Mats across calls to avoid per-call
 * allocation, matching the rest of the pipeline. Use one instance per thread
 * if you ever need concurrency.
 */
public final class LowLightEnhancer {

    private final CLAHE clahe = Imgproc.createCLAHE(2.0, new Size(8, 8));

    private final Mat grayScratch = new Mat();
    private final Mat labScratch = new Mat();
    private final Mat labLScratch = new Mat();
    private final Mat enhancedScratch = new Mat();
    private final List<Mat> labChannels = new ArrayList<>(3);

    /** Mean brightness of a BGR image on the 0-255 scale. */
    public double brightness(Mat bgr) {
        Imgproc.cvtColor(bgr, grayScratch, Imgproc.COLOR_BGR2GRAY);
        return Core.mean(grayScratch).val[0];
    }

    /**
     * Returns a contrast-boosted version of {@code bgr} when its mean
     * brightness is below {@code meanBrightnessThreshold}; otherwise returns
     * {@code bgr} unchanged with no copy.
     *
     * The enhanced result is an internal scratch buffer, valid only until the
     * next call on this instance — copy it if you need to keep it.
     */
    public Mat enhanceIfLowLight(Mat bgr, double meanBrightnessThreshold) {
        if (brightness(bgr) >= meanBrightnessThreshold) {
            return bgr;
        }

        Imgproc.cvtColor(bgr, labScratch, Imgproc.COLOR_BGR2Lab);

        labChannels.clear();
        Core.split(labScratch, labChannels);

        clahe.apply(labChannels.get(0), labLScratch);
        labLScratch.copyTo(labChannels.get(0));

        Core.merge(labChannels, labScratch);
        Imgproc.cvtColor(labScratch, enhancedScratch, Imgproc.COLOR_Lab2BGR);

        return enhancedScratch;
    }
}
