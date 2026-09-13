package com.GymGate.Camera;



import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;


class MatConverter {

    // reused across frames — NOT thread-safe, call from a single producer thread
    private static Mat bgraScratch = new Mat();
    private static byte[] pixelScratch;

    // JavaFX-owned state — must only ever be touched on the FX Application Thread
    private static WritableImage imageScratch;
    private static int lastWidth = -1, lastHeight = -1;

    /**
     * Converts a Mat to raw BGRA pixel bytes. Pure OpenCV/array work —
     * safe to call from any thread (e.g. the camera capture thread).
     * Does NOT touch any JavaFX object.
     */
    static FrameBytes matToBgraBytes(Mat mat) {
        int width = mat.width();
        int height = mat.height();

        if (mat.channels() == 1) {
            Imgproc.cvtColor(mat, bgraScratch, Imgproc.COLOR_GRAY2BGRA);
        } else {
            Imgproc.cvtColor(mat, bgraScratch, Imgproc.COLOR_BGR2BGRA);
        }

        int needed = width * height * 4;
        if (pixelScratch == null || pixelScratch.length != needed) {
            pixelScratch = new byte[needed];
        }
        bgraScratch.get(0, 0, pixelScratch);

        return new FrameBytes(pixelScratch, width, height);
    }

    /**
     * Uploads raw BGRA bytes into the reusable WritableImage.
     * MUST be called on the JavaFX Application Thread (e.g. from inside
     * Platform.runLater) — WritableImage/PixelWriter are not thread-safe
     * and calling this off the FX thread can race with the FX render
     * thread reading the same image, causing intermittent glitches/freezes.
     */
    static Image toImage(FrameBytes frameBytes) {
        int width = frameBytes.width();
        int height = frameBytes.height();

        if (imageScratch == null || lastWidth != width || lastHeight != height) {
            imageScratch = new WritableImage(width, height);
            lastWidth = width;
            lastHeight = height;
        }

        imageScratch.getPixelWriter().setPixels(0, 0, width, height,
                PixelFormat.getByteBgraInstance(), frameBytes.pixels(), 0, width * 4);

        return imageScratch;
    }

    /** Plain data carrier — no JavaFX types, so it's safe to hand across threads. */
    record FrameBytes(byte[] pixels, int width, int height) {}
}
