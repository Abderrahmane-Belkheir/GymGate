package com.GymGate.Ai.models;

import ai.onnxruntime.*;

import java.nio.file.Path;

/**
 * Thin wrapper around an OrtSession with performance-tuned SessionOptions.
 *
 * Thread counts are chosen per model "role" rather than one fixed number for
 * everything:
 *   - DETECTOR (SCRFD-2.5G): a tiny backbone. It doesn't benefit much from
 *     extra intra-op threads — the per-op matmuls are too small to split
 *     efficiently — and over-threading it just steals cycles from the
 *     recognizer that runs right after it in the same pipeline. 1-2 threads
 *     is the sweet spot.
 *   - RECOGNIZER (ArcFace ResNet-50 / w600k_r50): a much heavier conv net.
 *     ResNet50's conv/matmul ops are big enough to actually parallelize
 *     well, so it benefits from more intra-op threads, up to the point of
 *     diminishing returns / contention with the rest of the app.
 *
 * Also attempts to register a hardware execution provider (CUDA, DirectML,
 * or CoreML, in that order) before falling back to pure CPU. This is wrapped
 * in try/catch per provider because the native EP libraries may simply not
 * be present on a given machine/build — that's expected and not an error,
 * so we silently fall back rather than crashing model load.
 */
public class OnnxModel implements AutoCloseable {

    public enum Role {
        /** SCRFD-2.5G: small backbone, few threads, runs every frame. */
        DETECTOR,
        /** ArcFace ResNet-50 (w600k_r50): heavier backbone, more threads. */
        RECOGNIZER
    }

    private final OrtSession session;
    private final boolean usingHardwareAcceleration;

    public OnnxModel(Path model) {
        this(model, Role.DETECTOR, true);
    }

    public OnnxModel(Path model, Role role) {
        this(model, role, true);
    }

    /**
     * @param model               path to the .onnx file
     * @param role                tunes intra-op thread count for this model's compute profile
     * @param tryHardwareAccel    attempt GPU/NPU execution providers before falling back to CPU
     */
    public OnnxModel(Path model, Role role, boolean tryHardwareAccel) {
        try {
            OrtSession.SessionOptions options = new OrtSession.SessionOptions();

            options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            options.setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL);
            options.setInterOpNumThreads(1);
            options.setIntraOpNumThreads(intraOpThreadsFor(role));

            options.setMemoryPatternOptimization(true);
            options.setCPUArenaAllocator(true);

            // Spinning (busy-wait between ops) shaves a little scheduling
            // latency but keeps ORT's native threads burning a core each even
            // when idle — and those native threads are NOT stopped by a JVM
            // signal, so on an IDE stop / taskkill they keep the process warm
            // after everything else has gone. For a ~6fps check-in kiosk the
            // latency gain isn't worth that, so blocking is the default. Flip
            // GYMGATE_ORT_SPIN=1 to opt back in. Harmless if the ORT build
            // doesn't recognise the key.
            String spin = "1".equals(System.getenv("GYMGATE_ORT_SPIN")) ? "1" : "0";
            trySetConfigEntry(options, "session.intra_op.allow_spinning", spin);
            trySetConfigEntry(options, "session.inter_op.allow_spinning", spin);

            boolean accelerated = false;
            if (tryHardwareAccel) {
                accelerated = tryAddHardwareExecutionProvider(options);
            }
            this.usingHardwareAcceleration = accelerated;

            session = OnnxRuntimeManager.environment().createSession(model.toString(), options);

        } catch (OrtException e) {
            throw new RuntimeException(e);
        }
    }

    private static int intraOpThreadsFor(Role role) {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        return switch (role) {
            // SCRFD-2.5G is small; 2 threads was already benchmark-confirmed
            // fastest for it. Never exceed available cores.
            case DETECTOR -> Math.min(2, cores);
            // ResNet50 has enough per-op work to profitably use more
            // threads. Leave at least one core free for the rest of the
            // app (camera capture, UI, etc.) on small machines.
            case RECOGNIZER -> Math.max(1, Math.min(4, cores - 1 <= 0 ? cores : cores - 1));
        };
    }

    private static void trySetConfigEntry(OrtSession.SessionOptions options, String key, String value) {
        try {
            options.addConfigEntry(key, value);
        } catch (OrtException | NoSuchMethodError | UnsatisfiedLinkError ignored) {
            // Older ORT builds may not support a given config key — safe to skip.
        }
    }

    /**
     * Tries execution providers roughly in order of "most likely to be
     * fast and available" for a desktop deployment. Each addXxx() call is
     * independently guarded: if the native provider library isn't bundled
     * or the platform doesn't support it, OrtException is thrown and we
     * just move on to the next candidate / CPU fallback.
     */
    private static boolean tryAddHardwareExecutionProvider(OrtSession.SessionOptions options) {
        try {
            options.addCUDA();
            return true;
        } catch (Throwable ignored) {
            // No NVIDIA GPU / CUDA provider available.
        }
        try {
            options.addDirectML(0);
            return true;
        } catch (Throwable ignored) {
            // Not on Windows / no DirectML provider available.
        }
        try {
            options.addCoreML();
            return true;
        } catch (Throwable ignored) {
            // Not on macOS / no CoreML provider available.
        }
        return false; // falls back to CPU EP, which is always registered by default
    }

    public OrtSession session() {
        return session;
    }

    /** True if a GPU/NPU execution provider was successfully registered for this session. */
    public boolean isUsingHardwareAcceleration() {
        return usingHardwareAcceleration;
    }

    /**
     * Releases the native OrtSession (and, with it, its intra/inter-op thread
     * pools). Idempotent; never throws — called on shutdown where there is
     * nothing useful to do with a failure.
     */
    @Override
    public void close() {
        try {
            session.close();
        } catch (Throwable t) {
            System.err.println("OnnxModel: session close failed — " + t.getMessage());
        }
    }
}
