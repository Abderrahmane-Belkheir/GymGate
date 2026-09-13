package com.GymGate.UI.component;

import com.GymGate.Camera.CameraView;
import com.GymGate.bussines.services.I18nService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Polyline;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.javafx.FontIcon;
import org.opencv.core.Mat;

/**
 * Represents the live facial-recognition camera feed. Video fills the
 * entire panel edge-to-edge; the "Scanning for faces..." placeholder
 * (face icon + status text) auto-hides once real frames start arriving,
 * and reappears automatically if the camera disconnects.
 */
public class MainCameraPanel extends StackPane implements CameraView {

    private final ImageView liveFrameView = new ImageView();
    private final Label scanStatusLabel = new Label(I18nService.get("face_scanning"));
    private final VBox centerContent = new VBox();
    private final StackPane privacyOverlay = buildPrivacyOverlay();

    private boolean privacyModeEnabled = false;

    public MainCameraPanel() {

        getStyleClass().add("camera-panel");

        liveFrameView.setPreserveRatio(false);
        liveFrameView.fitWidthProperty().bind(widthProperty());
        liveFrameView.fitHeightProperty().bind(heightProperty());
        liveFrameView.getStyleClass().add("camera-frame-view");
        // Unmanaged: its fitWidth/fitHeight bindings above already keep it
        // pixel-perfect filling the panel, so it doesn't need to participate
        // in layout sizing too. Left managed, a real camera frame's native
        // pixel dimensions (e.g. 1280x720) can feed into this StackPane's
        // own preferred/minimum width during layout — invisible with no
        // camera connected (no image set yet), but demanding extra width
        // the moment a live frame arrives, pushing the stats column off
        // the right edge of the window.
        liveFrameView.setManaged(false);

        StackPane centerOverlay = buildCenterOverlay();

        VBox overlay = new VBox();
        overlay.setPickOnBounds(false);
        VBox.setVgrow(centerOverlay, Priority.ALWAYS);
        overlay.getChildren().addAll(buildTopBar(), centerOverlay, buildBottomBar());

        getChildren().addAll(liveFrameView, overlay, privacyOverlay);   // privacyOverlay added last = drawn on top

        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        clip.setArcWidth(40);
        clip.setArcHeight(40);
        setClip(clip);
    }

    private HBox buildTopBar() {
        FontIcon liveDot = new FontIcon("fas-circle");
        liveDot.getStyleClass().add("live-dot");
        Label liveLabel = new Label("LIVE");
        liveLabel.getStyleClass().add("live-text");
        Label camLabel = new Label("Front Desk");
        camLabel.getStyleClass().add("camera-meta-text");

        HBox leftGroup = new HBox(8, liveDot, liveLabel, camLabel);
        leftGroup.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        FontIcon videoIcon = new FontIcon("fas-video");
        videoIcon.getStyleClass().add("camera-meta-icon");
        Label resolutionLabel = new Label("1080p \u00B7 30fps");
        resolutionLabel.getStyleClass().add("camera-meta-text");
        FontIcon expandIcon = new FontIcon("fas-expand-alt");
        expandIcon.getStyleClass().add("camera-meta-icon");

        HBox rightGroup = new HBox(10, videoIcon, resolutionLabel, expandIcon);
        rightGroup.setAlignment(Pos.CENTER_RIGHT);

        HBox topBar = new HBox(leftGroup, spacer, rightGroup);
        topBar.getStyleClass().add("camera-top-bar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(14, 18, 14, 18));
        return topBar;
    }

    private StackPane buildCenterOverlay() {
        StackPane center = new StackPane();

        centerContent.getChildren().add(scanStatusLabel);
        centerContent.setAlignment(Pos.CENTER);

        scanStatusLabel.getStyleClass().add("scan-status-label");
        scanStatusLabel.setWrapText(true);
        scanStatusLabel.setMaxWidth(360);
        scanStatusLabel.setAlignment(Pos.CENTER);
        scanStatusLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        StackPane.setAlignment(centerContent, Pos.CENTER);
        center.getChildren().addAll(buildScanFrame(), centerContent);
        return center;
    }

    private StackPane buildScanFrame() {
        StackPane frame = new StackPane();
        frame.setMaxWidth(260);
        frame.setMaxHeight(160);

        Polyline topLeft = bracket(0, 22, 0, 0, 22, 0);
        Polyline topRight = bracket(-22, 0, 0, 0, 0, 22);
        Polyline bottomLeft = bracket(0, -22, 0, 0, 22, 0);
        Polyline bottomRight = bracket(-22, 0, 0, 0, 0, -22);

        StackPane.setAlignment(topLeft, Pos.TOP_LEFT);
        StackPane.setAlignment(topRight, Pos.TOP_RIGHT);
        StackPane.setAlignment(bottomLeft, Pos.BOTTOM_LEFT);
        StackPane.setAlignment(bottomRight, Pos.BOTTOM_RIGHT);

        frame.getChildren().addAll(topLeft, topRight, bottomLeft, bottomRight);
        return frame;
    }

    private Polyline bracket(double... points) {
        Polyline line = new Polyline(points);
        line.getStyleClass().add("scan-bracket");
        return line;
    }

    private HBox buildBottomBar() {
        IconTile aiTile = new IconTile("fas-brain", "icon-tile-blue-soft", 13);

        Label aiActiveLabel = new Label("AI Recognition Active");
        aiActiveLabel.getStyleClass().add("camera-bottom-title");
        Label thresholdLabel = new Label("Match confidence threshold");
        thresholdLabel.getStyleClass().add("camera-bottom-subtitle");
        VBox aiTextBox = new VBox(2, aiActiveLabel, thresholdLabel);

        HBox leftGroup = new HBox(12, aiTile, aiTextBox);
        leftGroup.setAlignment(Pos.CENTER_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox bottomBar = new HBox(leftGroup, spacer);
        bottomBar.getStyleClass().add("camera-bottom-bar");
        bottomBar.setAlignment(Pos.CENTER_LEFT);
        bottomBar.setPadding(new Insets(14, 20, 14, 20));
        return bottomBar;
    }

    public ImageView getLiveFrameView() {
        return liveFrameView;
    }

    private StackPane buildPrivacyOverlay() {
        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("camera-privacy-overlay");
        overlay.setVisible(false);
        overlay.setManaged(false);
        overlay.setMaxWidth(Double.MAX_VALUE);
        overlay.setMaxHeight(Double.MAX_VALUE);

        FontIcon icon = new FontIcon("fas-eye-slash");
        icon.getStyleClass().add("privacy-overlay-icon");

        Label label = new Label(I18nService.get("Camera_view_hidden"));
        label.getStyleClass().add("privacy-overlay-label");

        VBox content = new VBox(14, icon, label);
        content.setAlignment(Pos.CENTER);

        overlay.getChildren().add(content);
        return overlay;
    }

    public void setPrivacyMode(boolean hidden) {
        privacyModeEnabled = hidden;
        privacyOverlay.setVisible(hidden);
        privacyOverlay.setManaged(hidden);
    }

    @Override
    public void updateFrame(Image image) {
        if (privacyModeEnabled||image==null) {
            return;
        }
        liveFrameView.setImage(image);
        if (centerContent.isVisible()) {
            centerContent.setVisible(false);
            centerContent.setManaged(false);
        }
    }

    @Override
    public void consumeMat(Mat mat) {
        if (mat != null) {
            mat.release();
        }
    }

    public void setScanStatus(String text) {
        scanStatusLabel.setText(text);
    }


    /** Brings back the placeholder with a plug-it-back-in prompt for the front desk. */
    @Override
    public void showDisconnectedState() {
        liveFrameView.setImage(null);
        centerContent.setVisible(true);
        centerContent.setManaged(true);
        scanStatusLabel.setText(I18nService.get("Camera_disconnected_hint"));
    }

    /** Resets the status text once frames resume after a reconnect. */
    @Override
    public void showScanningState() {
        scanStatusLabel.setText(I18nService.get("face_scanning"));
        // centerContent itself gets hidden again by updateFrame() once a real frame arrives
    }
}