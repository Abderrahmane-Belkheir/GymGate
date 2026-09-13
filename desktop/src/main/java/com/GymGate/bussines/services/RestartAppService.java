package com.GymGate.bussines.services;

import com.GymGate.Ai.FaceProcessor;
import com.GymGate.Camera.CameraCapturingService;
import com.GymGate.Camera.CameraManager;
import com.GymGate.Launcher;
import javafx.application.Platform;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;


public final class RestartAppService {



    public static CameraCapturingService cameraCapturingService;
    public static Stage mainStage;
    public static Stage secondaryStage;

    public static void restart() {
        try {

            Launcher.pendingRestartCommand = ProcessHandle.current().info().command()
                        .orElseThrow(() -> new IllegalStateException("Cannot determine executable path"));
            shutdown();
        } catch (Exception e) {
            throw new RuntimeException("Failed to restart application", e);
        }
    }

    public static void shutdown(){
       releaseResources();

        Platform.runLater(() -> {
           if(mainStage!=null) {
               mainStage.close();
           }
           if(secondaryStage!=null){
             secondaryStage.close();
            }
            Platform.exit();
        });
    }

    /**
     * Stops every background thread the app spins up — the sync scheduler, the
     * camera health watchdog and the capture/face-processing threads. Split out
     * from {@link #shutdown()} so {@code GymGateEntry.stop()} can reuse it on a
     * plain window close (where the stage is already closing and Platform.exit()
     * has already fired). Idempotent; never throws.
     */
    public static void releaseResources(){
       SyncingService.stopIfRunning();
       ReminderScheduler.stopIfRunning();

       CameraManager cameraManager = CameraManager.getInstance();
       if (cameraManager != null) {
           cameraManager.stopHealthMonitor();
       }
       if(cameraCapturingService!=null){
           cameraCapturingService.stop();
       }
       // After the capture / face-processing threads are down, nothing runs
       // inference any more — free the ONNX sessions and their native thread
       // pools (which a JVM signal alone will not stop).
       try {
           FaceProcessor.shutdown();
       } catch (Throwable t) {
           System.err.println("releaseResources: FaceProcessor shutdown failed — " + t.getMessage());
       }
    }

}