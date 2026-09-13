package com.GymGate.Camera;


import ai.onnxruntime.OrtException;
import com.GymGate.bussines.services.I18nService;
import com.GymGate.bussines.services.RestartAppService;
import javafx.application.Platform;


import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Single entry point for standing up the camera subsystem:
 *   1. Loads the compiled-in capture calibration table
 *   2. Discovers which connected device the build has a profile for
 *   3. Initializes and starts CameraService against the resolved device
 *
 * This is the only class the rest of the app (e.g. HomeController) needs to
 * talk to for camera startup — it hides profile loading, discovery, and
 * CameraService construction behind one call.
 */
public class CameraManager {

    private final CameraView mainView;
    private final CameraView secondayView;

    private final CaptureProfileService profileService;
    private final CameraDiscoveryService discoveryService;

    // Swapped out by ensureCameraConnected() on the camera-health-monitor thread
    // while FX-thread methods (registrationView/mainView/attachExtraView/
    // isRunning) read it — volatile so those reads see the reconnect.
    private volatile CameraCapturingService cameraService;
    private CameraDeviceInfo activeDevice;
    // Enrollment camera panels attached by the register/edit dialogs. Tracked
    // here (not just on cameraService) so they can be re-attached to a fresh
    // capture service after a disconnect/reconnect.
    private final List<CameraView> extraViews = new CopyOnWriteArrayList<>();
    /** How often the watchdog polls the camera link. The common case — a camera
     *  unplugged while running — doesn't wait for this: the capture service
     *  fires {@link #onLinkLost()} the instant it notices. The poll is the
     *  backstop for "no camera at launch, plugged in later" (no event to fire). */
    private static final long HEALTH_CHECK_INTERVAL_SECONDS = 4;
    /** Floor between real reconnect attempts (webcam enumeration + VideoCapture
     *  open) so the event trigger and a poll landing together can't double-fire,
     *  and a permanently-absent camera isn't retried faster than this. Reset the
     *  moment the camera is healthy again. */
    private static final long RECONNECT_BACKOFF_MS = 3_000;
    private volatile ScheduledExecutorService healthMonitor;
    private volatile long lastReconnectAttemptMs = 0;

    private static CameraManager instance;

    private CameraManager(CameraView mainView,CameraView secondayView) throws OrtException {
        this.mainView = mainView;
        this.secondayView=secondayView;
        this.profileService = new CaptureProfileService();
        this.discoveryService = new CameraDiscoveryService();
    }


    public synchronized boolean startCamera() {
        CaptureProfileSet profileSet;
        try {
            profileSet = profileService.load();
        } catch (RuntimeException e) {
            System.err.println("Failed to load camera configuration: " + e.getMessage());
            status(I18nService.get("Camera_config_error"));
            return false;
        }

        Optional<CameraDeviceInfo> resolved;
        try {
            resolved = discoveryService.resolveCamera(profileSet);
        } catch (RuntimeException e) {
            System.err.println("Camera discovery failed: " + e.getMessage());
            status(I18nService.get("Camera_not_detected_hint"));
            return false;
        }

        if (resolved.isEmpty()) {
            System.err.println("No usable camera could be resolved.");
            status(I18nService.get("Camera_not_detected_hint"));
            return false;
        }

        activeDevice = resolved.get();

        try {
            cameraService = new CameraCapturingService(mainView,secondayView);
            cameraService.setOnLinkLost(this::onLinkLost);
            cameraService.start(activeDevice.getIndex());
            for (CameraView extraView : extraViews) {
                cameraService.addView(extraView);
            }
            RestartAppService.cameraCapturingService=cameraService;
            status(I18nService.get("face_scanning"));
            return true;
        } catch (RuntimeException e) {
            System.err.println("Failed to start camera " + activeDevice + ": " + e.getMessage());
            e.printStackTrace();
            status(I18nService.get("Camera_start_failed_hint"));
            cameraService = null;
            activeDevice = null;
            return false;
        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Starts the background watchdog that keeps the camera link alive: every
     * {@value #HEALTH_CHECK_INTERVAL_SECONDS}s it checks whether frames are
     * still flowing and, if not, rediscovers + reconnects. Idempotent — safe to
     * call more than once. Call after the initial {@link #startCamera()}.
     */
    public synchronized void startHealthMonitor() {
        if (healthMonitor != null) {
            return;
        }
        healthMonitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "camera-health-monitor");
            t.setDaemon(true);
            return t;
        });
        healthMonitor.scheduleWithFixedDelay(this::healthTick,
                HEALTH_CHECK_INTERVAL_SECONDS, HEALTH_CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /** Stops the watchdog (app shutdown / restart). */
    public synchronized void stopHealthMonitor() {
        if (healthMonitor != null) {
            healthMonitor.shutdownNow();
            healthMonitor = null;
        }
    }

    private void healthTick() {
        try {
            ensureCameraConnected();
        } catch (Exception e) {
            System.err.println("Camera health check failed: " + e.getMessage());
        }
    }

    /** Called by the capture service the moment it loses the camera — runs an
     *  immediate reconnect on the monitor thread instead of waiting for the next
     *  poll. Fired from the capture thread, so it only enqueues work. */
    private void onLinkLost() {
        ScheduledExecutorService monitor = healthMonitor;
        if (monitor != null) {
            try {
                monitor.execute(this::healthTick);
            } catch (RejectedExecutionException ignored) {
                // monitor stopped (shutting down) — the poll, if any, will cover it
            }
        }
    }

    /**
     * Checks the camera link and, if it's down, re-establishes it:
     *
     * <ul>
     *   <li>camera never started (unplugged at launch) → retry discovery + start;</li>
     *   <li>running camera dropped (unplugged while on) → tear the dead capture
     *       down so its native handle/threads are freed, then rediscover and
     *       start again on whatever profiled device is now present.</li>
     * </ul>
     *
     * A no-op while frames are flowing. When the camera is down, an actual
     * reconnect attempt (slow: webcam enumeration + {@code VideoCapture} open)
     * runs at most once per {@value #RECONNECT_BACKOFF_MS}ms so an
     * indefinitely-absent camera isn't hammered every check — but the first
     * attempt after a drop fires immediately. Safe to call off the FX thread;
     * status-text updates are marshalled onto it.
     */
    public synchronized void ensureCameraConnected() {
        if (cameraService != null && cameraService.isHealthy()) {
            lastReconnectAttemptMs = 0; // healthy — a fresh drop retries at once
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastReconnectAttemptMs < RECONNECT_BACKOFF_MS) {
            return; // tried recently, still down — don't re-run discovery yet
        }
        lastReconnectAttemptMs = now;

        if (cameraService != null) {
            System.out.println("Camera link lost — attempting to reconnect...");
            try {
                cameraService.stop();
            } catch (RuntimeException e) {
                System.err.println("Error tearing down dead camera service: " + e.getMessage());
            }
            cameraService = null;
            RestartAppService.cameraCapturingService = null;
            activeDevice = null;
        }

        if (startCamera()) {
            System.out.println("Camera connected: " + activeDevice);
        }
    }

    /** Runs a camera-view status update on the FX thread, whichever thread calls in. */
    private void status(String message) {
        if (Platform.isFxApplicationThread()) {
            mainView.setScanStatus(message);
        } else {
            Platform.runLater(() -> mainView.setScanStatus(message));
        }
    }

    public void attachExtraView(CameraView view) {
        if (!extraViews.contains(view)) extraViews.add(view);
        CameraCapturingService service = cameraService;
        if (service != null) service.addView(view);
    }

    public void detachExtraView(CameraView view) {
        extraViews.remove(view);
        CameraCapturingService service = cameraService;
        if (service != null) service.removeView(view);
    }

    /** The fixed public-facing-screen view (never swapped, unlike {@link #cameraService}) —
     *  used by callers that need to drive it directly (e.g. freezing it on a
     *  just-captured registration photo). */
    public CameraView secondaryView() {
        return secondayView;
    }

    public void mainView(){
        if (cameraService != null) cameraService.changeView(View.MAIN);
    }

    public void registrationView(){
        if (cameraService != null) cameraService.changeView(View.REGISTRATION);
    }

    public void stop(){
        if (cameraService != null) cameraService.stop();
    }

    /** True once a camera has been successfully resolved and started. */
    public boolean isRunning(){
        return cameraService != null;
    }

    public static void init(CameraView mainView,CameraView secondayView) throws OrtException {
        instance=new CameraManager(mainView,secondayView);
    }

    public static CameraManager getInstance(){
        return instance;
    }

}