package com.GymGate.Camera;


import javafx.scene.image.Image;
import org.opencv.core.Mat;

public interface CameraView {

    void updateFrame(Image image);

    void consumeMat(Mat mat);

    void setScanStatus(String status);

    void showScanningState();
    void showDisconnectedState();
}
