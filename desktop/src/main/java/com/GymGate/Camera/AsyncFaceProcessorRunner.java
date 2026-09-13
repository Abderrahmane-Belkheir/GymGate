package com.GymGate.Camera;

import ai.onnxruntime.OrtException;
import com.GymGate.Ai.FaceProcessor;
import com.GymGate.bussines.services.MemberValidationService;
import org.opencv.core.Mat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

class AsyncFaceProcessorRunner {

    private final MemberValidationService validationService;
    private final FaceProcessor processor;

    private final ExecutorService executor;
    private final AtomicReference<Mat> pendingFrame = new AtomicReference<>();
    /** The worker thread, so {@link #submitFrame}/{@link #shutdown} can wake it
     *  from {@link LockSupport#park()}. */
    private volatile Thread worker;
    private volatile View view = View.MAIN;
    private volatile boolean running = true;
    private volatile boolean processing = false;

    AsyncFaceProcessorRunner() throws OrtException {
        this.validationService = MemberValidationService.getInstance();
        this.processor = FaceProcessor.getInstance();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "face-processing-worker");
            t.setDaemon(true);
            return t;
        });

        this.executor.submit(this::runLoop);
    }

    /**
     * Submit the latest camera frame.
     * If a previous frame hasn't been processed yet, it is dropped.
     */
    void submitFrame(Mat frame) {
        if (frame == null || frame.empty())
            return;

        Mat clone = frame.clone();
        Mat old = pendingFrame.getAndSet(clone);

        if (old != null) {
            old.release();
        }

        // Wake the worker if it's parked waiting for a frame.
        Thread w = worker;
        if (w != null) {
            LockSupport.unpark(w);
        }
    }

    boolean isBusy() {
        return processing || pendingFrame.get() != null;
    }

    /** Whether the last detection pass found a face anywhere in frame — see
     *  {@link FaceProcessor#isFacePresent()}. Lets {@link CameraCapturingService}
     *  drive the secondary screen's live-feed/logo toggle without reaching
     *  into FaceProcessor (or its checked {@code getInstance()}) directly. */
    boolean isFacePresent() {
        return processor.isFacePresent();
    }

    /**
     * Runs for the entire lifetime of the worker thread (submitted exactly once
     * to a single-thread executor). Every branch below must either `continue`
     * the loop or fall through to the next iteration - a bare `return` here
     * would end the executor's task and permanently stop all future frame
     * processing, even after switching back to MAIN view.
     */
    private void runLoop() {
        worker = Thread.currentThread();
        while (running) {

            Mat frame = pendingFrame.getAndSet(null);

            if (frame == null) {
                // Sleep until submitFrame() (or shutdown()) unparks us instead
                // of polling every 2ms — the camera only submits at ~6fps, so
                // that poll was ~500 wasted wake-ups a second. park() can also
                // return spuriously; the loop just re-checks and parks again.
                LockSupport.park();
                continue;
            }

            // While the registration camera view is active we deliberately skip
            // recognition, but the loop itself keeps running so recognition
            // resumes as soon as we're back on the main view.
            if (view == View.REGISTRATION) {
                frame.release();
                continue;
            }

            processing = true;
            try {
                processor.process(frame).ifPresent(validationService::process);
            } catch (Exception e) {
                System.err.println("Face processing error: " + e.getMessage());
            } finally {
                frame.release();
                processing = false;
            }
        }
    }

    void changeView(View view) {
        this.view = view;
    }

    void shutdown() {
        running = false;
        Thread w = worker;
        if (w != null) {
            LockSupport.unpark(w); // in case it's parked waiting for a frame
        }
        executor.shutdownNow();

        // Wait for a frame that's mid-inference to finish. The ONNX sessions are
        // closed right after this returns (FaceProcessor.shutdown), and closing
        // a session with a run still in flight can crash the native side.
        try {
            if (!executor.awaitTermination(2, java.util.concurrent.TimeUnit.SECONDS)) {
                System.err.println("WARNING: face-processing worker did not stop within timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        Mat pending = pendingFrame.getAndSet(null);
        if (pending != null) {
            pending.release();
        }
    }
}