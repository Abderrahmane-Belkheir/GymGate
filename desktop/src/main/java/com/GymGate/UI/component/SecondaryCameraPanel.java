package com.GymGate.UI.component;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.List;

import com.GymGate.Camera.CameraView;
import com.GymGate.GymGateLogo;
import com.GymGate.bussines.db.entities.Member;
import com.GymGate.bussines.models.Settings;
import com.GymGate.bussines.models.ValidationResult;
import com.GymGate.bussines.services.I18nService;
import com.GymGate.bussines.services.RecognitionListener;
import com.GymGate.bussines.services.RestartAppService;
import com.GymGate.bussines.util.Converter;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.opencv.core.Mat;


public class SecondaryCameraPanel implements CameraView, RecognitionListener {

    private static final Duration RECOGNITION_HOLD = Duration.seconds(5);
    // The "member not found" screen carries no membership info to read, so it
    // clears sooner than a recognised result and frees the display for the
    // next person faster.
    private static final Duration NOT_FOUND_HOLD = Duration.seconds(2);

    private static final int WINDOWED_WIDTH = 800;
    private static final int WINDOWED_HEIGHT = 480;

    // logo shown until the first real camera frame arrives / while disconnected
    private static final double LOGO_WIDTH = 200;

    /** How long the live feed keeps showing after the last frame with a
     *  detected face, before falling back to the logo. Long enough to ride out
     *  a normal momentary detection gap (a head turn, one motion-blurred frame)
     *  during a real approach without visibly flickering between the two —
     *  matches {@code FaceProcessor.UNRESOLVED_TRACK_TTL_MS}'s tolerance for
     *  the same kind of gap, rather than inventing a second, possibly
     *  mismatched number. Turning the feed back ON has no such delay: there is
     *  no cost to showing it the instant a face reappears, since a real
     *  verdict needs several more frames anyway. */
    private static final long FACE_ABSENCE_GRACE_MS = 400;

    /** Wall-clock time face presence was last reported true. 0 initially, which
     *  correctly means "show the logo" on the very first call — a huge
     *  {@code now - 0} always exceeds the grace window. */
    private long lastFacePresentMs = 0;

    /** Duration of the white capture-flash flash overlay's fade-out — the
     *  "a photo was just taken" cue shown in lockstep with the registration
     *  dialog's own capture card, see {@link #showFrozenImage}. */
    private static final Duration CAPTURE_FLASH_DURATION = Duration.millis(350);

    private Stage stage;
    private ImageView imageView;
    private ImageView logoView;
    private Label statusLabel;
    private Pane captureFlash;

    /** True while the registration/update photo step has frozen this screen
     *  on a just-captured shot (see {@link #showFrozenImage}) — while true,
     *  neither {@link #updateFrame} nor {@link #setFacePresence} may touch
     *  {@code imageView}/{@code logoView}, or the live feed would immediately
     *  overwrite the frozen photo on the very next preview tick. */
    private volatile boolean frozen = false;


    private Pane blackout;


    private PauseTransition holdTimer;

    private final boolean usingSecondaryScreen;

    // ---- Post-recognition state screens (templates only — see fill/show
    // methods below). Nothing here is wired into onRecognition() yet; that's
    // intentional, hook it up wherever you decide the blackout hold should
    // hand off to one of these instead of just clearing. ----
    private Pane activeTwoBoxScene;
    private Label activeTwoBoxNameLabel;
    private Label activeTwoBoxEndDateValue;
    private Label activeTwoBoxRemainingValue;

    private Pane activeUnlimitedScene;
    private Label activeUnlimitedNameLabel;
    private Label activeUnlimitedEndDateValue;

    private Pane expiredByDateScene;
    private Label expiredByDateNameLabel;
    private Label expiredByDateEndDateValue;

    private Pane expiredNoRemainingScene;
    private Label expiredNoRemainingNameLabel;
    private Label expiredNoRemainingValue;

    // Shown when recognition ends with no confirmed member (UNKNOWN_FACE or an
    // unconfirmed AMBIGUOUS match — see MemberValidationService). Static text,
    // so no name/value labels to keep around.
    private Pane memberNotFoundScene;

    private List<Pane> stateScenes;

    public SecondaryCameraPanel() {
        Screen target = findSecondaryScreen();
        this.usingSecondaryScreen = target != null;
        onFx(() -> build(target));
    }


    private static Screen findSecondaryScreen() {
        List<Screen> screens = Screen.getScreens();
        Screen primary = Screen.getPrimary();
        for (Screen screen : screens) {
            if (!screen.equals(primary)) {
                return screen;
            }
        }
        return null;
    }
    private void build(Screen target) {

        // No second monitor -> this panel is never shown. Skip building its
        // whole scene graph (a second Scene + StackPane, four membership-state
        // template panes, several ImageViews and a Stage) and leave every field
        // null: updateFrame/onRecognition/showDisconnectedState/close all already
        // null-guard, so the panel becomes an inert no-op sink for the frames
        // and recognition events the pipeline still hands it.
        if (target == null) {
            return;
        }

        imageView = new ImageView();
        imageView.setPreserveRatio(true);
        ColorAdjust brightness = new ColorAdjust();
        brightness.setBrightness(0.5);
        imageView.setEffect(brightness);
        logoView = new ImageView(loadLogoImage());
        logoView.setPreserveRatio(true);
        logoView.setFitWidth(LOGO_WIDTH);

        statusLabel = new Label();
        statusLabel.setTextFill(Color.WHITE);
        statusLabel.setFont(Font.font(28));

        blackout = new Pane();
        blackout.setStyle("-fx-background-color: black;");
        blackout.setVisible(false);

        captureFlash = new Pane();
        captureFlash.setStyle("-fx-background-color: white;");
        captureFlash.setMouseTransparent(true);
        captureFlash.setOpacity(0);
        captureFlash.setVisible(false);

        activeTwoBoxScene = buildActiveTwoBoxScene();
        activeUnlimitedScene = buildActiveUnlimitedScene();
        expiredByDateScene = buildExpiredByDateScene();
        expiredNoRemainingScene = buildExpiredNoRemainingScene();
        memberNotFoundScene = buildMemberNotFoundScene();
        stateScenes = List.of(activeTwoBoxScene, activeUnlimitedScene, expiredByDateScene,
                expiredNoRemainingScene, memberNotFoundScene);

        StackPane root = new StackPane(
                imageView,
                logoView,
                statusLabel,
                activeTwoBoxScene,
                activeUnlimitedScene,
                expiredByDateScene,
                expiredNoRemainingScene,
                memberNotFoundScene,
                blackout,
                captureFlash
        );

        root.setStyle("-fx-background-color: black;");

        StackPane.setAlignment(statusLabel, Pos.BOTTOM_CENTER);
        StackPane.setAlignment(logoView, Pos.CENTER);

        imageView.fitWidthProperty().bind(root.widthProperty());
        imageView.fitHeightProperty().bind(root.heightProperty());

        Scene scene = new Scene(root, Color.BLACK);

        stage = new Stage();
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setScene(scene);

        RestartAppService.secondaryStage = stage;

        holdTimer = new PauseTransition(RECOGNITION_HOLD);
        holdTimer.setOnFinished(e -> {
            blackout.setVisible(false);
            hideAllStateScenes();
        });

        if (target != null) {
            Rectangle2D bounds = target.getBounds(); // full bounds, NOT getVisualBounds()

            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());

            stage.setAlwaysOnTop(true);

            stage.show();
        }


    }

    // ---- Scene builders — one per case from the mockup: active with a
    // countable remaining-days number, active on an unlimited plan (end date
    // only), expired by date, expired by hitting zero remaining days. Each
    // returns a Pane sized for the 800x480 panel, hidden by default, added to
    // the root StackPane in build() above but never shown automatically. ----

    private Pane buildActiveTwoBoxScene() {
        activeTwoBoxNameLabel = nameLabel();
        activeTwoBoxEndDateValue = statBoxValueLabel();
        activeTwoBoxRemainingValue = statBoxValueLabel();

        VBox endDateBox = statBox("تاريخ الانتهاء", "fas-calendar-alt", activeTwoBoxEndDateValue);
        VBox remainingBox = statBox("الأيام المتبقية", "fas-hourglass-half", activeTwoBoxRemainingValue);
        HBox boxRow = new HBox(18, endDateBox, remainingBox);
        boxRow.setAlignment(Pos.CENTER);

        return stateScene(checkBadge(), activeTwoBoxNameLabel, boxRow);
    }

    private Pane buildActiveUnlimitedScene() {
        activeUnlimitedNameLabel = nameLabel();
        activeUnlimitedEndDateValue = statBoxValueLabel();

        VBox endDateBox = statBox("تاريخ الانتهاء", "fas-calendar-alt", activeUnlimitedEndDateValue);

        return stateScene(checkBadge(), activeUnlimitedNameLabel, endDateBox);
    }

    private Pane buildExpiredByDateScene() {
        expiredByDateNameLabel = nameLabel();
        expiredByDateEndDateValue = statBoxValueLabel();

        VBox endDateBox = statBox("انتهت في", "fas-calendar-times", expiredByDateEndDateValue);

        return stateScene(xBadge(), expiredByDateNameLabel, endDateBox);
    }

    private Pane buildExpiredNoRemainingScene() {
        expiredNoRemainingNameLabel = nameLabel();
        expiredNoRemainingValue = statBoxValueLabel();

        VBox remainingBox = statBox("الأيام المتبقية", "fas-hourglass-end", expiredNoRemainingValue);

        return stateScene(xBadge(), expiredNoRemainingNameLabel, remainingBox);
    }

    private Pane buildMemberNotFoundScene() {
        Label message = nameLabel();
        message.setText("لم يتم العثور على العضو");

        Label hint = new Label("يرجى المحاولة مرة أخرى");
        hint.setTextFill(Color.web("#A9B4CC"));
        hint.setFont(Font.font("System", FontWeight.MEDIUM, 18));

        return stateScene(xBadge(), message, hint);
    }

    // ---- Shared building blocks for the four scenes above ----

    private Pane stateScene(StackPane badge, Label name, Region content) {
        Label footer = footerLabel();
        VBox inner = new VBox(22, badge, name, content, footer);
        inner.setAlignment(Pos.CENTER);
        VBox.setMargin(footer, new Insets(24, 0, 0, 0));
        inner.setStyle("-fx-background-color: #0A1120; -fx-background-radius: 10; "
                + "-fx-border-color: #2F6BFF; -fx-border-radius: 10; -fx-border-width: 2;");
        inner.setPadding(new Insets(28));

        StackPane screen = new StackPane(inner);
        StackPane.setAlignment(inner, Pos.CENTER);
        screen.setPrefSize(WINDOWED_WIDTH, WINDOWED_HEIGHT);
        screen.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        screen.setStyle("-fx-background-color: #060B14;");
        screen.setPadding(new Insets(24));
        screen.setVisible(false);
        screen.setManaged(false);

        ColorAdjust brighten = new ColorAdjust();
        brighten.setBrightness(0.4);
        screen.setEffect(brighten);

        return screen;
    }

    private StackPane checkBadge() {
        return badge("#10331E", "#22C55E", "fas-check", "#22C55E");
    }

    private StackPane xBadge() {
        return badge("#331010", "#EF4444", "fas-times", "#EF4444");
    }

    private StackPane badge(String fillHex, String strokeHex, String iconLiteral, String iconHex) {
        Circle circle = new Circle(44);
        circle.setFill(Color.web(fillHex));
        circle.setStroke(Color.web(strokeHex));
        circle.setStrokeWidth(3);

        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(44);
        icon.setIconColor(Color.web(iconHex));

        StackPane badge = new StackPane(circle, icon);
        badge.setMaxSize(88, 88);
        badge.setPrefSize(88, 88);
        return badge;
    }

    private Label nameLabel() {
        Label label = new Label();
        label.setTextFill(Color.WHITE);
        label.setFont(Font.font("System", FontWeight.MEDIUM, 38));
        return label;
    }

    private Label statBoxValueLabel() {
        Label label = new Label();
        label.setTextFill(Color.WHITE);
        label.setFont(Font.font("System", FontWeight.BOLD, 26));
        return label;
    }

    private VBox statBox(String labelText, String iconLiteral, Label valueLabel) {
        FontIcon captionIcon = new FontIcon(iconLiteral);
        captionIcon.setIconSize(16);
        captionIcon.setIconColor(Color.web("#A9B4CC"));

        Label caption = new Label(labelText);
        caption.setTextFill(Color.web("#A9B4CC"));
        caption.setFont(Font.font("System", FontWeight.MEDIUM, 13));

        HBox captionRow = new HBox(7, captionIcon, caption);
        captionRow.setAlignment(Pos.CENTER);

        VBox box = new VBox(8, captionRow, valueLabel);
        box.setAlignment(Pos.CENTER);
        box.setStyle("-fx-background-color: #101B30; -fx-background-radius: 8; "
                + "-fx-border-color: #2F6BFF; -fx-border-radius: 8; -fx-border-width: 2;");
        box.setPadding(new Insets(14, 26, 14, 26));
        box.setMinWidth(170);
        return box;
    }

    private Label footerLabel() {
        Label label = new Label(gymNameOrFallback());
        label.setTextFill(Color.web("#6B7CA0"));
        label.setFont(Font.font("System", FontWeight.MEDIUM, 17));
        FontIcon icon = new FontIcon("fas-building");
        icon.setIconSize(15);
        icon.setIconColor(Color.web("#6B7CA0"));
        label.setGraphic(icon);
        label.setGraphicTextGap(7);
        return label;
    }

    private String gymNameOrFallback() {
        String name = Settings.getGymName();
        return (name == null || name.isBlank()) ? "" : name;
    }

    // ---- Fill methods — set the text on an already-built scene. These do
    // NOT show/hide anything by themselves; call showStateScene(...) (below)
    // after filling, whenever/wherever you decide to wire this up. ----

    public void fillActiveTwoBoxScene(String name, String endDate, String remainingDaysText) {
        activeTwoBoxNameLabel.setText(name);
        activeTwoBoxEndDateValue.setText(endDate);
        activeTwoBoxRemainingValue.setText(remainingDaysText);
    }

    public void fillActiveUnlimitedScene(String name, String endDate) {
        activeUnlimitedNameLabel.setText(name);
        activeUnlimitedEndDateValue.setText(endDate);
    }

    public void fillExpiredByDateScene(String name, String endDate) {
        expiredByDateNameLabel.setText(name);
        expiredByDateEndDateValue.setText(endDate);
    }

    public void fillExpiredNoRemainingScene(String name, String remainingDaysText) {
        expiredNoRemainingNameLabel.setText(name);
        expiredNoRemainingValue.setText(remainingDaysText);
    }

    // ---- Visibility helper — hides the other three state scenes and shows
    // the one passed in. Not called anywhere yet; wire it in wherever the
    // recognition flow should hand off to one of these. ----

    public void showStateScene(Pane scene) {
        for (Pane s : stateScenes) {
            boolean match = s == scene;
            s.setVisible(match);
            s.setManaged(match);
        }
    }

    public void hideAllStateScenes() {
        for (Pane s : stateScenes) {
            s.setVisible(false);
            s.setManaged(false);
        }
    }

    public Pane getActiveTwoBoxScene() { return activeTwoBoxScene; }
    public Pane getActiveUnlimitedScene() { return activeUnlimitedScene; }
    public Pane getExpiredByDateScene() { return expiredByDateScene; }
    public Pane getExpiredNoRemainingScene() { return expiredNoRemainingScene; }


    private static Image loadLogoImage() {
        try {
            byte[] bytes = Base64.getDecoder().decode(GymGateLogo.BASE64_PNG);
            return new Image(new ByteArrayInputStream(bytes));
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void updateFrame(Image image) {

        if (image == null||imageView==null||frozen){
            return;
        }
        imageView.setImage(image);
        statusLabel.setVisible(false);
        if (logoView != null) {
            logoView.setVisible(false);
        }
    }

    /**
     * Freezes this screen on a just-captured registration/update photo — shown
     * on the public-facing screen at exactly the same moment the same shot
     * freezes the registration dialog's own capture card ({@code
     * EnrollmentCameraPanel.showFrozenImage}), plus a quick white flash to read
     * as "a photo was taken" rather than the feed simply stopping. Call
     * {@link #resumeLiveFeed()} afterwards (retake, or the photo step ending)
     * or the live feed can never come back — {@link #updateFrame} and
     * {@link #setFacePresence} both no-op while frozen.
     */
    public void showFrozenImage(Image image) {
        if (imageView == null || image == null) {
            return;
        }
        frozen = true;
        imageView.setImage(image);
        imageView.setVisible(true);
        if (logoView != null) {
            logoView.setVisible(false);
        }
        playCaptureFlash();
    }

    /** Resumes the normal live-feed/logo behavior after {@link #showFrozenImage}. */
    public void resumeLiveFeed() {
        frozen = false;
        // Treat this as a fresh "face last seen now" so the feed doesn't
        // instantly fall back to the logo before the next real detection
        // result arrives.
        lastFacePresentMs = System.currentTimeMillis();
    }

    private void playCaptureFlash() {
        if (captureFlash == null) {
            return;
        }
        captureFlash.setVisible(true);
        captureFlash.setOpacity(0.85);
        FadeTransition fade = new FadeTransition(CAPTURE_FLASH_DURATION, captureFlash);
        fade.setFromValue(0.85);
        fade.setToValue(0);
        fade.setOnFinished(e -> captureFlash.setVisible(false));
        fade.play();
    }

    /**
     * Called on every preview tick (~15fps, FX thread — same call site as
     * {@link #updateFrame}) with whether the detector's most recent pass found
     * a face anywhere in frame. Toggles {@code imageView} and {@code logoView}
     * in lockstep (never both visible): {@code logoView} is only
     * {@link #LOGO_WIDTH} wide and centered, so it does NOT cover the
     * full-screen {@code imageView} by z-order alone the way it does at
     * startup / disconnect, where {@code imageView} additionally has its image
     * cleared to {@code null}. Here the feed keeps receiving fresh frames the
     * whole time (so it's instantly current the moment it reappears), so
     * hiding it explicitly is required — otherwise the live feed still fills
     * the screen around the small logo.
     *
     * <p>Instant on the moment a face is seen; only reverts to the logo after
     * {@link #FACE_ABSENCE_GRACE_MS} of continuous absence (see that constant).
     * No-ops on a single-monitor setup where this panel was never built —
     * {@code CameraCapturingService} calls this unconditionally every tick,
     * same as {@link #updateFrame}.
     */
    public void setFacePresence(boolean present) {
        if (logoView == null || imageView == null || frozen) {
            return;
        }
        long now = System.currentTimeMillis();
        if (present) {
            lastFacePresentMs = now;
            logoView.setVisible(false);
            imageView.setVisible(true);
        } else if (now - lastFacePresentMs > FACE_ABSENCE_GRACE_MS) {
            logoView.setVisible(true);
            imageView.setVisible(false);
        }
    }

    @Override
    public void consumeMat(Mat mat) {
        if (mat != null) {
            mat.release();
        }
    }

    @Override
    public void setScanStatus(String status) {
        onFx(() -> {
            if (statusLabel == null) return;
            statusLabel.setText(status == null ? "" : status);
            statusLabel.setVisible(true);
        });
    }

    @Override
    public void showScanningState() {
        setScanStatus(I18nService.get("face_scanning"));
    }

    @Override
    public void showDisconnectedState() {
        onFx(() -> {
            if (imageView == null) return;
            imageView.setImage(null);
            statusLabel.setText(I18nService.get("Camera_disconnected"));
            statusLabel.setVisible(true);
            if (logoView != null) {
                logoView.setVisible(true);
            }
        });
    }


    private static final java.time.format.DateTimeFormatter DATE_FORMAT =
            java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Override
    public void onRecognition(ValidationResult result) {
        if (blackout == null) return;
        statusLabel.setVisible(false);
        // A new verdict always replaces whatever is on screen — clear the
        // blackout so a state scene is never left hidden behind a stale one
        // when verdicts arrive less than RECOGNITION_HOLD apart. The default
        // branch below turns it back on for the cases that still use it.
        blackout.setVisible(false);
        // Recognised results hold for the full read time; the not-found case
        // overrides this to NOT_FOUND_HOLD below.
        holdTimer.setDuration(RECOGNITION_HOLD);

        Member member = result.getMember();
        String name = member != null ? Converter.capitalize(member.getFirstName())+" "+Converter.capitalize(member.getLastName()) : "";

        switch (result.getStatus()) {
            case SUCCESS,ALREADY_CHECKED_IN -> {
                String endDate = member.getEndDate() != null ? member.getEndDate().format(DATE_FORMAT) : "";
                if (member.getRemainingDays() == null) {
                    fillActiveUnlimitedScene(name, endDate);
                    showStateScene(activeUnlimitedScene);
                } else {
                    fillActiveTwoBoxScene(name, endDate, member.getRemainingDays() + " يوماً");
                    showStateScene(activeTwoBoxScene);
                }
            }
            case PLAN_EXPIRED -> {
                String endDate = member.getEndDate() != null ? member.getEndDate().format(DATE_FORMAT) : "";
                fillExpiredByDateScene(name, endDate);
                showStateScene(expiredByDateScene);
            }
            case NO_REMAINING_DAYS -> {
                fillExpiredNoRemainingScene(name, "0 يوم");
                showStateScene(expiredNoRemainingScene);
            }
            // POSSIBLE_MATCH is not confirmed yet — staff haven't acted on it,
            // so this public-facing screen shows nothing more than a plain
            // "not found" until (if ever) they accept it and a real SUCCESS
            // verdict arrives. Never name a candidate here.
            case MEMBER_NOT_FOUND, POSSIBLE_MATCH -> {
                holdTimer.setDuration(NOT_FOUND_HOLD);
                showStateScene(memberNotFoundScene);
            }
            default -> {
                // NO_ACTIVE_PLAN — no membership-info screen for it, so fall back
                // to the existing blackout+status behavior instead of a state scene.
                hideAllStateScenes();
                blackout.setVisible(true);
                holdTimer.playFromStart();
                return;
            }
        }

        holdTimer.playFromStart();
    }


    public boolean isUsingSecondaryScreen() {
        return usingSecondaryScreen;
    }

    public void close() {
        onFx(() -> {
            if (holdTimer != null) holdTimer.stop();
            if (stage != null) stage.close();
        });
    }

    private static void onFx(Runnable action) {
        if (Platform.isFxApplicationThread()) {
            action.run();
        } else {
            Platform.runLater(action);
        }
    }
}