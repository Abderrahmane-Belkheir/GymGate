package com.GymGate.Camera;

import ai.onnxruntime.OrtException;
import com.GymGate.UI.component.SecondaryCameraPanel;
import javafx.application.Platform;
import javafx.scene.image.Image;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.opencv.videoio.Videoio;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class CameraCapturingService {

    private VideoCapture camera;
    private Thread captureThread;

    private final CameraView mainView;
    private final CameraView secondaryView;
    // CopyOnWriteArrayList: addView()/removeView() can be called from the FX
    // thread (dialog setup/teardown) while the capture thread iterates this
    // list on every frame — needs to be safe for concurrent iteration.
    private final List<CameraView> enrollmentViews = new CopyOnWriteArrayList<>();
    private volatile View view = View.MAIN;

    private AsyncFaceProcessorRunner runner;
    /** How long the feed may show "no signal" — a failed grab, or (once real
     *  video has been seen) black frames — before the link is declared lost.
     *  Wall-clock, not a frame count: the DirectShow backend the packaged build
     *  opens keeps returning {@code read()==true} with black frames after an
     *  unplug, but only at ~1fps, so a frame-count threshold took ~50s to fire.
     *  MSMF (what the IDE run opens) fails the grab outright and trips this in
     *  well under the timeout. */
    private static final long NO_SIGNAL_TIMEOUT_MS = 2_000;
    /** Sum of the per-channel mean below which a frame is considered "no signal".
     *  A genuinely dark room still has sensor noise well above this; a
     *  disconnected DirectShow feed is exactly zero. */
    private static final double BLACK_FRAME_MEAN_SUM = 2.0;

    private static final long DETECTION_INTERVAL_NS = 160_000_000L; // 6 FPS
    /** The preview feed is throttled to this rate independently of how fast the
     *  camera delivers frames. Frames still have to be read at the camera's rate
     *  to keep the driver buffer drained (low latency), but resizing, colour
     *  converting, copying to a byte[], marshalling to the FX thread and
     *  repainting three ImageViews every one of them is wasted work — 15fps is
     *  indistinguishable for a monitoring preview and roughly halves the
     *  steady-state cost of the capture pipeline. Face detection keeps its own
     *  {@link #DETECTION_INTERVAL_NS} cadence and is unaffected. */
    private static final long PREVIEW_INTERVAL_NS = 66_000_000L; // ~15 FPS
    /** How often the all-black "no signal" check actually runs {@link Core#mean}
     *  over a full frame. A 2s {@link #NO_SIGNAL_TIMEOUT_MS} doesn't need 30
     *  full-frame reductions a second; ~6/s is plenty and keeps the reduction
     *  off the hot path. */
    private static final long BLACK_CHECK_INTERVAL_MS = 160;
    private volatile boolean running = true;
    // True while the capture loop is failing to read frames (camera unplugged
    // or gone unresponsive). Read from outside by CameraManager's periodic
    // health check so it can rediscover + reconnect. Flips back to false on its
    // own if the same device starts delivering frames again.
    private volatile boolean disconnected = false;
    // Fired once, the instant a loss of the camera link is detected, so the
    // reconnect can start immediately instead of waiting for the next poll.
    private volatile Runnable onLinkLost;

    private static final int CAPTURE_WIDTH_PX = 1280;
    private static final int CAPTURE_HEIGHT_PX = 720;


    private static final int PREVIEW_WIDTH_PX = 960;
    private final Mat previewScratch = new Mat();

    CameraCapturingService(CameraView mainView,CameraView  secondaryView) throws OrtException {
        this.mainView = mainView;
        this.secondaryView=secondaryView;
    }

    void start(int cameraIndex) throws OrtException {

        // Prefer the DirectShow backend on Windows: MSMF (the OpenCV default)
        // floods stderr with "videoio(MSMF): can't grab frame" the moment a
        // camera stalls or is unplugged, opens noticeably slower, and its device
        // indexing doesn't line up with webcam-capture's DirectShow enumeration
        // (what CameraDiscoveryService resolves the index from). Fall back to the
        // default backend if DirectShow can't open this device.
        camera = new VideoCapture(cameraIndex, Videoio.CAP_DSHOW);
        if (!camera.isOpened()) {
            System.err.println("DirectShow could not open camera " + cameraIndex
                    + " — falling back to the default backend");
            camera.release();
            camera = new VideoCapture(cameraIndex);
        }

        camera.set(Videoio.CAP_PROP_FRAME_WIDTH, CAPTURE_WIDTH_PX);
        camera.set(Videoio.CAP_PROP_FRAME_HEIGHT, CAPTURE_HEIGHT_PX);
        camera.set(Videoio.CAP_PROP_FPS, 30);
        if (!camera.isOpened()) {
            throw new RuntimeException("Cannot open camera: " + cameraIndex);
        }

        double actualWidth = camera.get(Videoio.CAP_PROP_FRAME_WIDTH);
        double actualHeight = camera.get(Videoio.CAP_PROP_FRAME_HEIGHT);
        System.out.println("Camera opened at " + actualWidth + "x" + actualHeight
                + " (requested " + CAPTURE_WIDTH_PX + "x" + CAPTURE_HEIGHT_PX + ")");
        if (actualWidth != CAPTURE_WIDTH_PX || actualHeight != CAPTURE_HEIGHT_PX) {
            System.err.println("WARNING: camera did not honor the requested capture resolution. "
                    + "Recognition quality and the distance-gating thresholds in FaceDetectionService "
                    + "were calibrated assuming ~" + CAPTURE_WIDTH_PX + "px width — verify this camera's "
                    + "actual supported modes if this persists.");
        }

        runner = new AsyncFaceProcessorRunner();

        captureThread = new Thread(this::captureLoop, "camera-capture-thread");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    private void captureLoop() {

        Mat frame = new Mat();

        // Black-frame detection only arms after the first real frame, so a
        // camera's dark warm-up right after open/reconnect can't be mistaken for
        // an unplug (which would tear down and rediscover in a loop).
        boolean seenLiveFrame = false;
        // Wall-clock time the feed first went quiet (failed grab, or black once
        // we've seen real video); 0 while frames are flowing normally.
        long noSignalSinceMs = 0;

        long lastDetection = 0;
        long lastPreviewNs = 0;
        long lastBlackCheckMs = 0;
        boolean blackFrame = false;

        while (camera.isOpened()&&running) {

            try {

                boolean success = camera.read(frame);
                boolean readable = success && !frame.empty();

                long nowMs = System.currentTimeMillis();

                // The full-frame black test (Core.mean) is comparatively costly;
                // run it a few times a second rather than once per grab and
                // carry the last verdict forward — a 2s no-signal timeout has
                // ample margin for that.
                if (!readable) {
                    blackFrame = false;
                } else if (nowMs - lastBlackCheckMs >= BLACK_CHECK_INTERVAL_MS) {
                    lastBlackCheckMs = nowMs;
                    blackFrame = isBlackFrame(frame);
                }

                if (readable && !blackFrame) {
                    seenLiveFrame = true;
                }

                // "No signal" = an unreadable grab, or — once real video has been
                // seen — a black frame: the DirectShow backend keeps returning
                // readable-but-black frames after an unplug instead of failing.
                boolean noSignal = !readable || (seenLiveFrame && blackFrame);

                if (!noSignal) {
                    noSignalSinceMs = 0;
                } else if (noSignalSinceMs == 0) {
                    noSignalSinceMs = nowMs;
                }

                boolean linkDown = noSignal && (nowMs - noSignalSinceMs) >= NO_SIGNAL_TIMEOUT_MS;

                if (noSignal) {

                    if (linkDown && !disconnected) {
                        disconnected = true;
                        notifyDisconnected();
                        fireLinkLost();
                        System.err.println("Camera appears disconnected"
                                + (readable ? " (no signal — frames are black)" : ""));
                    }

                    // Either still inside the grace window, or the link is down
                    // and reconnect is being driven from CameraManager now — in
                    // both cases don't render this bad frame. Poll slowly so a
                    // fast-failing backend can't spin, and so stderr isn't
                    // flooded with failed grabs while teardown catches up.
                    // stop() interrupts us out promptly.
                    try {
                        Thread.sleep(disconnected ? 500 : 100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                if (disconnected) {
                    disconnected = false;
                    notifyScanning();
                }

                long now = System.nanoTime();


                if (now - lastDetection >= DETECTION_INTERVAL_NS && !runner.isBusy()) {
                    runner.submitFrame(frame);
                    lastDetection = now;
                }

                // Preview pipeline (resize + colour convert + copy + FX repaint)
                // is throttled to ~15fps; frames in between are read to keep the
                // buffer drained and then dropped.
                if (now - lastPreviewNs < PREVIEW_INTERVAL_NS) {
                    continue;
                }
                lastPreviewNs = now;

                // Safe on this (background) thread: pure OpenCV + byte[] work,
                // no JavaFX objects are touched here.
                MatConverter.FrameBytes frameBytes = MatConverter.matToBgraBytes(buildPreview(frame));

                View currentView = view;
                // One clone per registered enrollment view: each EnrollmentCameraPanel
                // releases the Mat it's given independently (consumeMat()/close()), so
                // sharing a single Mat instance across multiple views would cause a
                // double-release of native memory once more than one panel is attached.
                List<Mat> framesForViews = (currentView == View.REGISTRATION && !enrollmentViews.isEmpty())
                        ? enrollmentViews.stream().map(v -> frame.clone()).toList()
                        : List.of();

                Platform.runLater(() -> {
                    // WritableImage/PixelWriter must only be touched on the FX
                    // Application Thread, so the actual Image is built here,
                    // not on the capture thread.
                    Image fxImage = MatConverter.toImage(frameBytes);
                    if (currentView == View.MAIN) {
                        mainView.updateFrame(fxImage);
                    } else {
                        for (int i = 0; i < enrollmentViews.size(); i++) {
                            CameraView enrollmentView = enrollmentViews.get(i);
                            if (i < framesForViews.size()) {
                                enrollmentView.consumeMat(framesForViews.get(i));
                            }
                            enrollmentView.updateFrame(fxImage);
                        }
                    }
                    secondaryView.updateFrame(fxImage);
                    // Live-feed/logo toggle for the public-facing screen only —
                    // read fresh here (not captured earlier on the capture
                    // thread) for the same reason fxImage is built here: the
                    // freshest value at the moment it's actually applied.
                    // instanceof is null-safe and false for every other
                    // CameraView, so this is a no-op for MainCameraPanel/
                    // EnrollmentCameraPanel and for a single-monitor setup
                    // where SecondaryCameraPanel never built its scene graph.
                    if (secondaryView instanceof SecondaryCameraPanel scp) {
                        // The face-processing worker skips detection entirely
                        // while the registration view is active (see
                        // AsyncFaceProcessorRunner.runLoop), so isFacePresent()
                        // is stale/frozen for the whole registration flow — it
                        // would otherwise leave the secondary screen stuck on
                        // whichever state (logo or feed) happened to be true
                        // the instant the dialog opened. During registration a
                        // person is being enrolled by definition, so just show
                        // the live feed; showFrozenImage()'s own frozen flag
                        // still wins once a shot is taken.
                        boolean present = currentView == View.REGISTRATION || runner.isFacePresent();
                        scp.setFacePresence(present);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Loop exited (device closed or stop() requested) — treat as a lost link
        // so the health check picks it up unless we're shutting down on purpose.
        if (running) {
            disconnected = true;
            fireLinkLost();
        }
        notifyDisconnected();
        frame.release();
    }

    /** True when the capture loop is alive and actually delivering frames. */
    boolean isHealthy() {
        return running
                && !disconnected
                && captureThread != null
                && captureThread.isAlive();
    }

    /** Sets a one-shot callback invoked the moment the camera link is lost. */
    void setOnLinkLost(Runnable onLinkLost) {
        this.onLinkLost = onLinkLost;
    }

    private void fireLinkLost() {
        Runnable callback = onLinkLost;
        if (callback != null) {
            try {
                callback.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    /** True when the frame carries essentially no light on any channel — the
     *  shape a disconnected DirectShow feed takes (exact zeros). Cheap: one
     *  vectorised {@link Core#mean} pass. */
    private boolean isBlackFrame(Mat frame) {
        Scalar mean = Core.mean(frame);
        double sum = 0;
        int channels = Math.min(3, frame.channels());
        for (int i = 0; i < channels; i++) {
            sum += mean.val[i];
        }
        return sum < BLACK_FRAME_MEAN_SUM;
    }

    private Mat buildPreview(Mat frame) {
        if (frame.cols() <= PREVIEW_WIDTH_PX) {
            return frame;
        }
        int previewHeight = (int) Math.round(frame.rows() * ((double) PREVIEW_WIDTH_PX / frame.cols()));
        Imgproc.resize(frame, previewScratch, new Size(PREVIEW_WIDTH_PX, previewHeight));
        return previewScratch;
    }

    private void notifyDisconnected() {
        Platform.runLater(() -> {
            mainView.showDisconnectedState();
            for (CameraView enrollmentView : enrollmentViews) {
                enrollmentView.showDisconnectedState();
            }
        });

    }

    private void notifyScanning() {
        Platform.runLater(() -> {
            mainView.showScanningState();
            for (CameraView enrollmentView : enrollmentViews) {
                enrollmentView.showScanningState();
            }
        });
    }

    void addView(CameraView enrollmentView) {
        if (!enrollmentViews.contains(enrollmentView)) {
            enrollmentViews.add(enrollmentView);
        }
    }

    void removeView(CameraView enrollmentView) {
        enrollmentViews.remove(enrollmentView);
    }

    void changeView(View view) {
        this.view = view;
        runner.changeView(view);
    }
    public void stop() {
        running = false;          // ask the loop to exit on its next check
        if (runner != null)
            runner.shutdown();

        if (captureThread != null) {
            captureThread.interrupt();      // best-effort, in case it's sleeping/blocked elsewhere
            try {
                captureThread.join(2000);   // wait for the loop to actually finish
                if (captureThread.isAlive()) {
                    System.err.println("WARNING: capture thread did not stop within timeout");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (camera != null)
            camera.release();     // only release once we know the loop has stopped touching it
    }
}