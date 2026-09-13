package com.GymGate.UI.component;


import com.GymGate.Camera.CameraView;
import javafx.animation.FadeTransition;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.opencv.core.Mat;

/**
 * Pure display component for the enrollment capture step — shows either
 * the live feed or a frozen captured photo, nothing else. All capture/
 * retake/confirm logic and buttons now live in RegisterMemberController.
 */
public class EnrollmentCameraPanel extends VBox implements CameraView {

    private static final double CARD_WIDTH = 300;
    private static final double CARD_HEIGHT = 220;

    /** Duration of the white capture-flash overlay's fade-out — see
     *  {@link #showFrozenImage}. Kept identical to
     *  {@code SecondaryCameraPanel.CAPTURE_FLASH_DURATION} so the two screens'
     *  flashes read as one simultaneous event, not two slightly different ones. */
    private static final Duration CAPTURE_FLASH_DURATION = Duration.millis(350);

    private final ImageView feedView = new ImageView();
    private final Pane captureFlash = new Pane();

    private Image lastLiveFrame;
    private Mat lastLiveMat;
    private boolean frozen = false;


    public EnrollmentCameraPanel() {
        setAlignment(Pos.CENTER);
        getChildren().add(buildCard());
    }



    private StackPane buildCard() {
        StackPane card = new StackPane();
        card.getStyleClass().add("capture-card");
        card.setPrefSize(CARD_WIDTH, CARD_HEIGHT);
        card.setMaxSize(CARD_WIDTH, CARD_HEIGHT);
        card.setMinSize(CARD_WIDTH, CARD_HEIGHT);

        feedView.setFitWidth(CARD_WIDTH);
        feedView.setFitHeight(CARD_HEIGHT);
        feedView.setPreserveRatio(false);

        Rectangle clip = new Rectangle(CARD_WIDTH, CARD_HEIGHT);
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        card.setClip(clip);

        captureFlash.setStyle("-fx-background-color: white;");
        captureFlash.setMouseTransparent(true);
        captureFlash.setOpacity(0);
        captureFlash.setVisible(false);

        card.getChildren().addAll(feedView, captureFlash);
        return card;
    }

    /** The most recent live frame received — used by the controller to capture a photo. */
    public Image getLastLiveFrame() {
        return lastLiveFrame;
    }

    public Mat getLastLiveMat() {
        return lastLiveMat;
    }

    /** Freezes the display on a specific image (the captured photo), with a
     *  quick white flash to read as "a photo was taken" rather than the feed
     *  simply stopping. */
    public void showFrozenImage(Image image) {
        frozen = true;
        feedView.setImage(image);
        playCaptureFlash();
    }

    private void playCaptureFlash() {
        captureFlash.setVisible(true);
        captureFlash.setOpacity(0.85);
        FadeTransition fade = new FadeTransition(CAPTURE_FLASH_DURATION, captureFlash);
        fade.setFromValue(0.85);
        fade.setToValue(0);
        fade.setOnFinished(e -> captureFlash.setVisible(false));
        fade.play();
    }

    /** Resumes showing the live feed (e.g. after Retake, or when re-entering this step). */
    public void resumeLiveFeed() {
        frozen = false;
        feedView.setImage(lastLiveFrame);
    }

    public void close() {
        if (lastLiveMat != null) {
            lastLiveMat.release();
        }
        lastLiveMat = null;
        lastLiveFrame = null;
        frozen = false;
    }
    // ---- CameraView ----

    @Override
    public void updateFrame(Image image) {
        lastLiveFrame = image;
        if (!frozen) {
            feedView.setImage(image);
        }
    }

    @Override
    public void consumeMat(Mat mat) {
        if (lastLiveMat != null) {
            lastLiveMat.release();
        }
        lastLiveMat = mat;
    }

    @Override public void setScanStatus(String status) {}
    @Override public void showDisconnectedState() { }
    @Override public void showScanningState() { }
}