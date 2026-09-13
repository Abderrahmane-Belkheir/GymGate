package com.GymGate.Ai.models;

import ai.onnxruntime.OrtEnvironment;

public final class OnnxRuntimeManager {

    private static final OrtEnvironment ENVIRONMENT =
            OrtEnvironment.getEnvironment();

    private OnnxRuntimeManager() {}

    public static OrtEnvironment environment() {
        return ENVIRONMENT;
    }

    /**
     * Closes the shared ORT environment. Call only after every OrtSession
     * created from it has been closed (see {@link ModelManager#close()}).
     * Idempotent-ish and never throws — shutdown path only.
     */
    public static void closeEnvironment() {
        try {
            ENVIRONMENT.close();
        } catch (Throwable t) {
            System.err.println("OnnxRuntimeManager: environment close failed — " + t.getMessage());
        }
    }
}
