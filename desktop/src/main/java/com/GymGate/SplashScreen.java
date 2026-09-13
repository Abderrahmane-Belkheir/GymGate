package com.GymGate;




import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.ParallelTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.BoxBlur;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.util.Base64;
import java.io.ByteArrayInputStream;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Full-window splash screen shown the instant the app launches, while the
 * real startup work (OpenCV, database, settings, i18n, ...) runs on a
 * background thread instead of blocking Application#init().
 *
 * Usage from GymGateEntry:
 *
 *   SplashScreen splash = new SplashScreen();
 *   splash.setSubtitle("Fast, secure check-in for your members");
 *   splash.show();
 *
 *   splash.setOnProceed(() -> {
 *       showMainStage(primaryStage);
 *       // Do NOT call splash.close() here — the splash fades itself out
 *       // and closes automatically after this runs.
 *   });
 *
 *   splash.runInitTask(reporter -> {
 *       int total = 4;
 *       reporter.report("Preparing camera", 1, total);
 *       OpenCV.loadLocally();
 *       ...
 *   });
 */
public class SplashScreen {

    /** Reports real startup progress — {@code stepIndex}/{@code totalSteps}
     *  drive the progress bar's actual fill fraction, {@code message} is
     *  shown alongside it. Not a simulated/timed progress source. */
    @FunctionalInterface
    public interface ProgressReporter {
        void report(String message, int stepIndex, int totalSteps);
    }

    @FunctionalInterface
    public interface InitWork {
        void run(ProgressReporter reporter) throws Exception;
    }

    /** Work that must run on the FX thread after the background work succeeds
     *  (e.g. building the main view's Scene) — completes before the Proceed
     *  button is ever revealed. */
    @FunctionalInterface
    public interface FxTask {
        void run() throws Exception;
    }

    private static final String ACCENT = "#2F8CFF";      // shield blue, matches the logo
    private static final String ACCENT_SOFT = "#5DA8FF";
    private static final String BG_TOP = "#0D111A";
    private static final String BG_BOTTOM = "#05070B";
    private static final String CARD_BORDER = "rgba(255,255,255,0.08)";
    private static final String TEXT_MUTED = "#8A93A6";
    private static final double BAR_WIDTH = 200;
    /** Minimum time between two consecutive real progress reports, in ns.
     *  Task#updateProgress()/updateMessage() coalesce rapid-fire calls onto
     *  a single FX pulse — if two real steps complete faster than this
     *  apart, an earlier percentage would be silently dropped and the bar
     *  would jump straight to the final value with nothing visible in
     *  between. This tops up only the *gap* between reports to this floor;
     *  if the real work already took this long or longer, it's a no-op. */
    private static final long MIN_STEP_INTERVAL_NANOS = 200_000_000L; // 200ms

    private final Stage stage = new Stage(StageStyle.UNDECORATED);

    /** Only ever shown for the failure/Retry state — no text is shown
     *  alongside the progress bar during normal startup. */
    private final Label statusLabel = new Label();
    private final Label subtitleLabel = new Label();
    /** Loading indicator: a plain white track with a blue fill whose width
     *  reflects REAL reported progress (see animateFillTo()) — not a timer.
     *  Each update animates smoothly to the new width over ~350ms so
     *  discrete step jumps still read as fluid motion. No text/labels. */
    private final Region loadingTrack = new Region();
    private final Region loadingFill = new Region();
    private StackPane loadingBar;
    private final Button proceedButton = new Button("Proceed to Main View");
    private final Label errorLabel = new Label();

    // Entrance-animation targets, built in buildLogo()/buildContent() and
    // animated in playEntranceAnimation() once the stage is actually showing.
    // The logo itself is NOT one of these — it's fully static (see buildLogo()).
    private ImageView logoView;
    private DropShadow logoGlow;
    private Label titleLabel;
    private VBox statusBlock;
    private Timeline fillAnimation;

    private Runnable onProceed;

    public SplashScreen() {
        stage.setResizable(false);
        stage.setAlwaysOnTop(true);
        Rectangle2D bounds = Screen.getPrimary().getBounds();
        Scene scene = buildScene(bounds.getWidth(), bounds.getHeight());
        stage.setScene(scene);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    /** Set the tagline / marketing text shown under the "GymGate" title. */
    public void setSubtitle(String text) {
        subtitleLabel.setText(text);
    }

    /** Wire up what happens once the launch transition has played. */
    public void setOnProceed(Runnable onProceed) {
        this.onProceed = onProceed;
    }

    public void show() {
        stage.show();
        playEntranceAnimation();
    }

    public void close() {
        stage.close();
    }

    /**
     * Runs {@code work} on a background thread so the splash stays responsive
     * (never blocks the JavaFX thread). Once it succeeds, {@code afterSuccess}
     * runs synchronously on the FX thread (e.g. to build the main view's
     * Scene) — the Proceed button is only revealed once BOTH have finished
     * AND the progress bar has visually reached 100%, so clicking Proceed
     * later never has to load or build anything itself. If either step
     * fails, an error state with a Retry button is shown instead of hanging.
     */
    public void runInitTask(InitWork work, FxTask afterSuccess) {
        setProceedButtonHidden();
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
        loadingBar.setVisible(true);
        loadingBar.setManaged(true);
        resetLoadingProgress();

        AtomicLong lastStepAt = new AtomicLong(System.nanoTime());

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                work.run((msg, stepIndex, totalSteps) -> {
                    // Task#updateMessage()/updateProgress() are safe to call
                    // from this background thread — they coalesce and marshal
                    // to the FX thread on their own, no manual Platform.runLater
                    // needed and no blocking of the UI thread either way. BUT
                    // that coalescing means if two real steps land less than
                    // one FX pulse apart, the earlier one never actually
                    // renders. Top up the gap since the last report to a
                    // small floor — on this background thread only, so the
                    // UI thread is never blocked — so every real step gets a
                    // chance to actually paint before the next one arrives.
                    // If real work already took this long, this is a no-op.
                    long now = System.nanoTime();
                    long remaining = MIN_STEP_INTERVAL_NANOS - (now - lastStepAt.get());
                    if (remaining > 0) {
                        try {
                            Thread.sleep(remaining / 1_000_000, (int) (remaining % 1_000_000));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    lastStepAt.set(System.nanoTime());

                    updateMessage(msg == null ? "" : msg);
                    updateProgress(stepIndex, Math.max(1, totalSteps));
                });
                return null;
            }
        };

        // Fires on the FX thread whenever real progress is reported — drives
        // the bar's fill width. No text/label is shown alongside it.
        task.progressProperty().addListener((obs, oldVal, newVal) -> {
            double fraction = newVal.doubleValue();
            if (fraction < 0) {
                return; // -1 = indeterminate; nothing reported yet
            }
            animateFillTo(BAR_WIDTH * Math.max(0, Math.min(1, fraction)), null);
        });

        task.setOnSucceeded(e -> {
            try {
                if (afterSuccess != null) {
                    afterSuccess.run();
                }
            } catch (Exception ex) {
                showFailure(ex, work, afterSuccess);
                return;
            }

            // The background steps only ever report up to "totalSteps of
            // totalSteps" for the work itself; finishing the main view's
            // Scene (afterSuccess, above) is the true last step, so it's
            // what finally takes the bar to a genuine 100% — the button
            // only appears once that's visually landed.
            animateFillTo(BAR_WIDTH, () -> {
                loadingBar.setVisible(false);
                loadingBar.setManaged(false);
                proceedButton.setText("متابعة");
                proceedButton.setOnAction(ev -> beginLaunchTransition());
                revealProceedButton();
            });
        });

        task.setOnFailed(e -> showFailure(task.getException(), work, afterSuccess));

        Thread thread = new Thread(task, "gymgate-init-thread");
        thread.setDaemon(true);
        thread.start();
    }

    /** Convenience overload when there's no FX-thread build step needed. */
    public void runInitTask(InitWork work) {
        runInitTask(work, null);
    }

    private void showFailure(Throwable ex, InitWork work, FxTask afterSuccess) {
        resetLoadingProgress();
        loadingBar.setVisible(false);
        loadingBar.setManaged(false);

        statusLabel.setText("Startup failed.");
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);

        errorLabel.setText(ex != null && ex.getMessage() != null
                ? ex.getMessage()
                : "An unexpected error occurred during startup.");
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);

        proceedButton.setText("Retry");
        proceedButton.setOnAction(ev -> runInitTask(work, afterSuccess));
        revealProceedButton();
    }

    // ------------------------------------------------------------------
    // Launch transition — everything already loaded, so just fade straight
    // into the main view (no extra loading step after the click).
    // ------------------------------------------------------------------

    private void beginLaunchTransition() {
        proceedButton.setDisable(true); // guard against double-clicks

        // Show the main stage NOW, while it's still fully hidden behind this
        // always-on-top, fully-opaque splash — its Scene's FXML was already
        // loaded back in runInitTask()'s afterSuccess step, so nothing needs
        // to be built or fetched here. Fading the splash out *after* this
        // (instead of closing the splash first and showing the main stage
        // once it's gone) means Home is revealed continuously as the splash
        // becomes transparent, with no gap where neither window is visible.
        if (onProceed != null) {
            onProceed.run();
        }

        // primaryStage.show() above still has to run Home's first real
        // CSS + layout pass — genuine one-time work, on this same pulse.
        // Starting the fade Timeline immediately would race that pass for
        // the render thread and could visibly stutter the first frames of
        // the fade, so let it land on the next pulse instead, once that
        // one-time cost is already paid, rather than competing with it.
        Platform.runLater(this::fadeOutAndClose);
    }

    /** Fades the splash window's own opacity to nothing, on top of the
     *  already-showing main stage, then closes it (pure cleanup by then —
     *  it's already fully transparent). Eased both ends, not linear — a
     *  linear opacity ramp reads as mechanical rather than smooth. */
    private void fadeOutAndClose() {
        Timeline fade = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(stage.opacityProperty(), 1.0, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(450), new KeyValue(stage.opacityProperty(), 0.0, Interpolator.EASE_BOTH))
        );
        fade.setOnFinished(e -> stage.close());
        fade.play();
    }

    // ------------------------------------------------------------------
    // UI construction
    // ------------------------------------------------------------------

    private Scene buildScene(double width, double height) {
        StackPane root = new StackPane();
        root.setPrefSize(width, height);

        root.getChildren().add(buildBackground(width, height));
        root.getChildren().add(buildAmbientParticles(width, height));
        root.getChildren().add(buildContent());

        return new Scene(root, width, height);
    }

    /**
     * Faint twinkling dots scattered across the whole window — ambient depth
     * for the scene as a whole, not just the logo. Deliberately lightweight:
     * a handful of dots, one fade-only animation each (no movement, which
     * would force a per-frame bounds/repaint recalculation per dot) — the
     * background init work (OpenCV, etc.) already competes for the same FX
     * thread, so this stays cheap rather than adding 40+ concurrent
     * animations on top of it.
     */
    private Pane buildAmbientParticles(double width, double height) {
        Pane layer = new Pane();
        layer.setPrefSize(width, height);
        layer.setMouseTransparent(true);

        int count = 9;
        for (int i = 0; i < count; i++) {
            double size = 1.5 + Math.random() * 2.5;
            Circle dot = new Circle(size);
            dot.setFill(Color.web(i % 4 == 0 ? ACCENT : "#FFFFFF"));
            dot.setLayoutX(Math.random() * width);
            dot.setLayoutY(Math.random() * height);
            dot.setOpacity(0.05);
            layer.getChildren().add(dot);

            double duration = 5 + Math.random() * 6;

            FadeTransition twinkle = new FadeTransition(Duration.seconds(duration / 2), dot);
            twinkle.setFromValue(0.05);
            twinkle.setToValue(0.2 + Math.random() * 0.2);
            twinkle.setAutoReverse(true);
            twinkle.setCycleCount(Animation.INDEFINITE);
            twinkle.setInterpolator(Interpolator.EASE_BOTH);
            twinkle.setDelay(Duration.seconds(Math.random() * duration));
            twinkle.play();
        }

        return layer;
    }

    /**
     * Full-bleed background: your gym photo, softly blurred and center-cropped
     * to cover the whole window, with a dark gradient scrim on top so the
     * card text stays readable. Falls back to a plain gradient if the image
     * resource isn't found, so the splash never breaks.
     */
    private Pane buildBackground(double width, double height) {
        Pane container = new Pane();
        container.setPrefSize(width, height);
        container.setClip(new Rectangle(width, height));

        Image image = loadImage("/images/gym-background.jpg");
        if (image != null) {
            ImageView imageView = new ImageView(image);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

            // Cover-fit: scale so the image fills the window with no gaps,
            // then center it (overflow is hidden by the clip above).
            double scale = Math.max(width / image.getWidth(), height / image.getHeight());
            double fitW = image.getWidth() * scale;
            double fitH = image.getHeight() * scale;
            imageView.setFitWidth(fitW);
            imageView.setFitHeight(fitH);
            imageView.setLayoutX((width - fitW) / 2);
            imageView.setLayoutY((height - fitH) / 2);

            // "Little blurry" — soft background blur, not a heavy gaussian smear.
            BoxBlur blur = new BoxBlur(8, 8, 3);
            imageView.setEffect(blur);

            container.getChildren().add(imageView);
        } else {
            Region fallback = new Region();
            fallback.setPrefSize(width, height);
            fallback.setStyle(
                    "-fx-background-color: linear-gradient(to bottom right, " + BG_TOP + ", " + BG_BOTTOM + ");"
            );
            container.getChildren().add(fallback);
        }

        Region overlay = new Region();
        overlay.setPrefSize(width, height);
        overlay.setStyle(
                "-fx-background-color: linear-gradient(to bottom right, rgba(6,8,13,0.78), rgba(4,5,9,0.85));"
        );
        container.getChildren().add(overlay);

        return container;
    }

    private Image loadImage(String resourcePath) {
        try {
            java.io.InputStream in = getClass().getResourceAsStream(resourcePath);
            if (in == null) {
                System.err.println("SplashScreen: resource not found on classpath: " + resourcePath
                        + " — check it's under src/main/resources and your build includes it.");
                return null;
            }
            Image image = new Image(in);
            if (image.isError()) {
                System.err.println("SplashScreen: failed to decode " + resourcePath + " — " + image.getException());
                return null;
            }
            return image;
        } catch (Exception ex) {
            System.err.println("SplashScreen: error loading " + resourcePath + ": " + ex);
            return null;
        }
    }

    private VBox buildContent() {
        StackPane logo = buildLogo();

        titleLabel = new Label("GymGate");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 44));
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setOpacity(0); // entrance-animated in playEntranceAnimation()

        subtitleLabel.setFont(Font.font("System", 16));
        subtitleLabel.setTextFill(Color.web(TEXT_MUTED));
        subtitleLabel.setWrapText(true);
        subtitleLabel.setTextAlignment(TextAlignment.CENTER);
        subtitleLabel.setMaxWidth(420);
        // Default placeholder — replace via setSubtitle(...) before show(),
        // e.g. splash.setSubtitle("Fast, secure check-in for your members");
        subtitleLabel.setText("Your tagline goes here");
        subtitleLabel.setOpacity(0); // entrance-animated

        // Loading indicator: a slightly rounded white track, with a blue
        // fill whose width reflects real reported progress — no default
        // ProgressBar chrome, no separate moving element.
        loadingTrack.setPrefSize(BAR_WIDTH, 4);
        loadingTrack.setMaxSize(BAR_WIDTH, 4);
        loadingTrack.setStyle("-fx-background-color: rgba(255,255,255,0.92); -fx-background-radius: 4;");

        loadingFill.setPrefSize(0, 4);
        loadingFill.setMinWidth(0);
        // Without this, StackPane stretches an unmanaged-max-width child to
        // fill the whole container on every layout pass regardless of its
        // prefWidth — the fill would always render at full width no matter
        // what the animation below sets prefWidth to. Locking maxWidth to
        // track prefWidth is what makes the fill actually narrower than the
        // track while it's still filling.
        loadingFill.setMaxWidth(Region.USE_PREF_SIZE);
        loadingFill.setMaxHeight(4);
        loadingFill.setStyle("-fx-background-color: " + ACCENT + "; -fx-background-radius: 4;");

        loadingBar = new StackPane(loadingTrack, loadingFill);
        loadingBar.setAlignment(Pos.CENTER_LEFT);
        loadingBar.setMaxSize(BAR_WIDTH, 4);
        StackPane.setAlignment(loadingFill, Pos.CENTER_LEFT);

        statusLabel.setFont(Font.font("System", 13));
        statusLabel.setTextFill(Color.web(TEXT_MUTED));
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);

        errorLabel.setFont(Font.font("System", 13));
        errorLabel.setTextFill(Color.web("#FF6B6B"));
        errorLabel.setWrapText(true);
        errorLabel.setMaxWidth(380);
        errorLabel.setTextAlignment(TextAlignment.CENTER);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        buildProceedButton();

        statusBlock = new VBox(12, loadingBar, statusLabel, errorLabel);
        statusBlock.setAlignment(Pos.CENTER);
        statusBlock.setOpacity(0); // entrance-animated

        VBox card = new VBox(22, logo, titleLabel, subtitleLabel, statusBlock, proceedButton);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(56, 64, 56, 64));
        card.setMaxWidth(560);
        card.setStyle(
                "-fx-background-color: rgba(18,21,28,0.55);" +
                        "-fx-background-radius: 28;" +
                        "-fx-border-color: " + CARD_BORDER + ";" +
                        "-fx-border-radius: 28;" +
                        "-fx-border-width: 1;"
        );
        DropShadow cardShadow = new DropShadow();
        cardShadow.setColor(Color.web("#000000", 0.5));
        cardShadow.setRadius(40);
        cardShadow.setSpread(0.05);
        card.setEffect(cardShadow);

        VBox wrapper = new VBox(card);
        wrapper.setAlignment(Pos.CENTER);
        wrapper.setPickOnBounds(false);
        return wrapper;
    }

    private void buildProceedButton() {
        proceedButton.setFont(Font.font("System", FontWeight.BOLD, 15));
        proceedButton.setTextFill(Color.WHITE);
        setDefaultProceedStyle();
        setProceedButtonHidden();
    }

    private void setDefaultProceedStyle() {
        proceedButton.setStyle(
                "-fx-background-color: linear-gradient(to right, " + ACCENT + ", " + ACCENT_SOFT + ");" +
                        "-fx-text-fill: *white;" +
                        "-fx-font-size: 15px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 14 36 14 36;" +
                        "-fx-background-radius: 28;" +
                        "-fx-cursor: hand;"
        );
    }

    private void setProceedButtonHidden() {
        proceedButton.setVisible(false);
        proceedButton.setManaged(false);
        proceedButton.setDisable(true);
        proceedButton.setOpacity(0);
    }

    private void revealProceedButton() {
        setDefaultProceedStyle();
        proceedButton.setVisible(true);
        proceedButton.setManaged(true);
        proceedButton.setDisable(false);
        FadeTransition fade = new FadeTransition(Duration.millis(400), proceedButton);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    /**
     * The logo and its glow, clipped to its own rounded footprint. Fully
     * static — full opacity/scale from the very first frame, no entrance
     * animation, no ongoing motion of any kind.
     */
    private StackPane buildLogo() {
        logoView = new ImageView();
        try {
            byte[] bytes = Base64.getDecoder().decode(GymGateLogo.BASE64_PNG);
            Image image = new Image(new ByteArrayInputStream(bytes));
            if (image.isError()) {
                System.err.println("SplashScreen: logo image failed to decode — "
                        + image.getException());
            } else {
                logoView.setImage(image);
            }
        } catch (Exception ex) {
            // Embedded logo should never fail to load, but log loudly
            // instead of silently showing a blank space if it ever does.
            System.err.println("SplashScreen: could not load embedded logo: " + ex);
        }
        logoView.setFitWidth(130);
        logoView.setFitHeight(130);
        logoView.setPreserveRatio(true);

        logoGlow = new DropShadow();
        logoGlow.setColor(Color.web(ACCENT, 0.65));
        logoGlow.setRadius(35);
        logoGlow.setSpread(0.18);
        logoView.setEffect(logoGlow);

        StackPane stack = new StackPane(logoView);
        stack.setMinSize(130, 130);
        stack.setMaxSize(130, 130);
        Rectangle clip = new Rectangle(130, 130);
        clip.setArcWidth(48);
        clip.setArcHeight(48);
        stack.setClip(clip);

        return stack;
    }

    // ------------------------------------------------------------------
    // Entrance animation — title/subtitle/status stagger in; the logo is
    // static and takes no part in this. All eased, none linear.
    // ------------------------------------------------------------------

    private void playEntranceAnimation() {
        ParallelTransition titleIn = staggeredIn(titleLabel, 0);
        ParallelTransition subtitleIn = staggeredIn(subtitleLabel, 120);
        ParallelTransition statusIn = staggeredIn(statusBlock, 240);

        new ParallelTransition(titleIn, subtitleIn, statusIn).play();
    }

    /** Fade + slight upward slide, delayed by {@code delayMillis} — used for
     *  the secondary elements that appear on entrance. */
    private ParallelTransition staggeredIn(Node node, int delayMillis) {
        FadeTransition fade = fadeIn(node, 500);

        TranslateTransition slide = new TranslateTransition(Duration.millis(500), node);
        slide.setFromY(14);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition in = new ParallelTransition(fade, slide);
        in.setDelay(Duration.millis(delayMillis));
        return in;
    }

    private FadeTransition fadeIn(Node node, int millis) {
        FadeTransition fade = new FadeTransition(Duration.millis(millis), node);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);
        return fade;
    }

    // ------------------------------------------------------------------
    // Progress bar — driven entirely by real reported progress
    // (task.progressProperty()), never by a fixed timer. No text/label,
    // just the fill width.
    // ------------------------------------------------------------------

    private void resetLoadingProgress() {
        if (fillAnimation != null) {
            fillAnimation.stop();
        }
        loadingFill.setPrefWidth(0);
    }

    /** Animates the bar to a new absolute width over a short, eased
     *  transition — the transition just smooths the visual jump between two
     *  real, discrete progress reports, it isn't simulating elapsed time
     *  on its own. */
    private void animateFillTo(double targetWidth, Runnable onComplete) {
        if (fillAnimation != null) {
            fillAnimation.stop();
        }
        fillAnimation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(loadingFill.prefWidthProperty(), loadingFill.getPrefWidth(), Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(350), new KeyValue(loadingFill.prefWidthProperty(), targetWidth, Interpolator.EASE_BOTH))
        );
        if (onComplete != null) {
            fillAnimation.setOnFinished(e -> onComplete.run());
        }
        fillAnimation.play();
    }
}
