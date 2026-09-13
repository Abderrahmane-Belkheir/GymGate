
package com.GymGate.bussines.util;

import com.GymGate.Launcher;
import com.GymGate.bussines.services.RestartAppService;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.CodeSource;
import java.util.List;
import java.util.Objects;

public final class AppPaths {

    private static final String DATA_SUBDIR = "data";
    private static final String MODELS_SUBDIR = "models";
    private static File cachedDataDir;
    private static File cachedModelsDir;


    private AppPaths() {}



    /** Per-user writable root, used ONLY when running from an installed (jpackage) build. */
    private static File resolveUserDataRoot() {
        String localAppData = System.getenv("LOCALAPPDATA");
        File root = (localAppData != null)
                ? new File(localAppData, "GymGate")
                : new File(System.getProperty("user.home"), ".gymgate");
        return root;
    }

    public static synchronized File getDataDir() {
        if (cachedDataDir != null) return cachedDataDir;
        File dataDir= new File(resolveUserDataRoot(), DATA_SUBDIR);
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new RuntimeException("Could not create data directory: " + dataDir.getAbsolutePath());
        }
        cachedDataDir = dataDir;
        return cachedDataDir;
    }



    public static  File resolve(String fileName){
        return new File(getDataDir(),fileName);
    }


    public static synchronized File getModelsDir() throws IOException {
        if (cachedModelsDir != null) return cachedModelsDir;

        File modelsDir=new File(resolveUserDataRoot(),MODELS_SUBDIR);

        if (!modelsDir.exists() && !modelsDir.mkdirs()) {
            throw new RuntimeException("could not create models directory");
        }

        cachedModelsDir = modelsDir;
        return cachedModelsDir;
    }

    public static void extractBundle(Path path, String fileName) throws IOException {
        try (InputStream in =AppPaths.class.getResourceAsStream("/models/"+fileName)) {
            if (in == null) {
                throw new IOException("Model '" + fileName + "' is not bundled (expected classpath"
                        + " resource /models/" + fileName + "). Check the recognizer.model name in"
                        + " Recognition.properties, or add the .onnx file to src/main/resources/models/.");
            }

            Files.copy(in,path,StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static File resolveModel(String fileName) throws IOException {
        File file=new File(getModelsDir(),fileName);
        if(!file.exists()){
            extractBundle(file.toPath(),fileName);
        }
        return new File(getModelsDir(), fileName);
    }

}