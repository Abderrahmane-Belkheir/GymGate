package com.GymGate.Ai.models;



import com.GymGate.Ai.recognition.RecognitionConfig;
import com.GymGate.bussines.services.RestartAppService;
import com.GymGate.bussines.util.AppPaths;

import java.net.URISyntaxException;
import java.nio.file.Path;



import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;

public final class ModelManager {

    private static final ModelManager INSTANCE;

    static {
        try {
            INSTANCE = new ModelManager();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private static final String DETECTOR_FILE_NAME = "scrfd_2.5g.onnx";

    private  OnnxModel detector;
    private  OnnxModel recognizer;

    private ModelManager() throws IOException {
        // Recognizer model file is chosen in Recognition.properties — see
        // RecognitionConfig. Resolved here (not a static field) so it can't be
        // read before this class's static initializers have run.
        String recognizerFile = RecognitionConfig.active().modelFile();
        detector = new OnnxModel(resolveModelFile(DETECTOR_FILE_NAME), OnnxModel.Role.DETECTOR);
        recognizer = new OnnxModel(resolveModelFile(recognizerFile), OnnxModel.Role.RECOGNIZER);
            System.out.println("SCRFD detector hardware acceleration: " + detector.isUsingHardwareAcceleration());
            System.out.println("ArcFace recognizer hardware acceleration: " + recognizer.isUsingHardwareAcceleration());

    }

    public static ModelManager getInstance() {
        return INSTANCE;
    }

    public OnnxModel detector() {
        return detector;
    }

    public OnnxModel recognizer() {
        return recognizer;
    }

    /**
     * Closes both ONNX sessions and the shared ORT environment, freeing the
     * native inference thread pools. Idempotent; never throws. Must be called
     * only once the face-processing pipeline has stopped (nothing may run
     * inference after this). See {@link com.GymGate.Ai.FaceProcessor#shutdown()}.
     */
    public synchronized void close() {
        if (detector != null) {
            detector.close();
            detector = null;
        }
        if (recognizer != null) {
            recognizer.close();
            recognizer = null;
        }
        OnnxRuntimeManager.closeEnvironment();
    }

    /**
     * Resolves a model to a real file under AppPaths.getModelsDir() — jar/exe-
     * relative, not user.dir-relative, same guarantee as gymgate.db. If it's
     * not there yet (first run after install,
     * or after an update that ships new model files), extracts it from the
     * bundled classpath resource once. After that, this is just a normal
     * on-disk file OnnxModel can open directly.
     */
    private Path resolveModelFile(String fileName) throws IOException { return AppPaths.resolveModel(fileName).toPath();
    }


}
