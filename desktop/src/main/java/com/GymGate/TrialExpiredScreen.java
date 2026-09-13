package com.GymGate;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import com.GymGate.bussines.services.LicenseService;

/**
 * Full-window "your trial has ended" screen. Arabic, right-to-left, styled to
 * match {@link SplashScreen}. The only action is to close the app.
 *
 * Shown in two situations:
 *   - {@link #show(Stage)} at startup, when {@link LicenseService#isTrialExpired()}
 *     is true and the app never really starts;
 *   - {@link #takeOver(Stage)} while the app is running, when Supabase rejects
 *     the API key with HTTP 401 (the publishable key was rotated/revoked
 *     server-side — a remote kill switch).
 */
public final class TrialExpiredScreen {

    private static final String ACCENT = "#2F8CFF";
    private static final String BG_TOP = "#0D111A";
    private static final String BG_BOTTOM = "#05070B";
    private static final String CARD_BORDER = "rgba(255,255,255,0.08)";
    private static final String TEXT_MUTED = "#9AA5B8";

    private Label titleLabel;
    private Label bodyLabel;
    private VBox contactBlock;

    /** Builds the screen on {@code stage} and shows it. Closing it exits the app. */
    public void show(Stage stage) {
        stage.setTitle("GymGate");
        stage.initStyle(StageStyle.UNDECORATED);
        stage.setResizable(false);

        Rectangle2D bounds = Screen.getPrimary().getBounds();
        Scene scene = buildScene(bounds.getWidth(), bounds.getHeight());
        scene.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        stage.setScene(scene);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.setOnCloseRequest(e -> Platform.exit());
        stage.show();

        playEntrance();
    }

    /**
     * Swaps this screen onto an already-configured stage (its style is already
     * set, so we can't call initStyle again) — used when the running app has to
     * be locked out because the Supabase key was revoked. Also works on a stage
     * that was configured but never shown.
     */
    public void takeOver(Stage stage) {
        Rectangle2D bounds = Screen.getPrimary().getBounds();
        Scene scene = buildScene(bounds.getWidth(), bounds.getHeight());
        scene.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

        stage.setOnCloseRequest(e -> Platform.exit());
        stage.setScene(scene);
        stage.setX(bounds.getMinX());
        stage.setY(bounds.getMinY());
        stage.setWidth(bounds.getWidth());
        stage.setHeight(bounds.getHeight());
        stage.setAlwaysOnTop(true);
        if (!stage.isShowing()) {
            stage.show();
        }
        stage.toFront();

        playEntrance();
    }

    private Scene buildScene(double width, double height) {
        StackPane root = new StackPane();
        root.setPrefSize(width, height);
        root.getChildren().add(buildBackground(width, height));
        root.getChildren().add(buildCard());
        return new Scene(root, width, height);
    }

    private VBox buildCard() {
        StackPane badge = new StackPane();
        badge.setMinSize(88, 88);
        badge.setMaxSize(88, 88);
        badge.setStyle(
                "-fx-background-color: rgba(47,140,255,0.14);" +
                        "-fx-background-radius: 999;" +
                        "-fx-border-color: rgba(47,140,255,0.35);" +
                        "-fx-border-radius: 999;" +
                        "-fx-border-width: 1;"
        );
        FontIcon icon = new FontIcon("fas-hourglass-end");
        icon.setIconSize(36);
        icon.setIconColor(Color.web("#5DA8FF"));
        badge.getChildren().add(icon);

        titleLabel = new Label("انتهت الفترة التجريبية");
        titleLabel.setFont(Font.font("System", FontWeight.BOLD, 34));
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setOpacity(0);

        bodyLabel = new Label(
                "لقد انتهت فترتك التجريبية من تطبيق GymGate.\n" +
                        "للاشتراك ومواصلة استخدام التطبيق، يُرجى التواصل معنا."
        );
        bodyLabel.setFont(Font.font("System", 17));
        bodyLabel.setTextFill(Color.web(TEXT_MUTED));
        bodyLabel.setWrapText(true);
        bodyLabel.setTextAlignment(TextAlignment.CENTER);
        bodyLabel.setMaxWidth(440);
        bodyLabel.setOpacity(0);

        contactBlock = buildContactBlock();
        contactBlock.setOpacity(0);

        Button closeButton = new Button("إغلاق التطبيق");
        closeButton.setFont(Font.font("System", FontWeight.BOLD, 15));
        closeButton.setStyle(
                "-fx-background-color: linear-gradient(to right, #2F8CFF, #5DA8FF);" +
                        "-fx-text-fill: white;" +
                        "-fx-font-size: 15px;" +
                        "-fx-font-weight: bold;" +
                        "-fx-padding: 13 40 13 40;" +
                        "-fx-background-radius: 26;" +
                        "-fx-cursor: hand;"
        );
        closeButton.setOnAction(e -> Platform.exit());

        VBox card = new VBox(20, badge, titleLabel, bodyLabel, contactBlock, closeButton);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(56, 64, 56, 64));
        card.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        card.setStyle(
                "-fx-background-color: rgba(18,21,28,0.62);" +
                        "-fx-background-radius: 28;" +
                        "-fx-border-color: " + CARD_BORDER + ";" +
                        "-fx-border-radius: 28;" +
                        "-fx-border-width: 1;"
        );
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.web("#000000", 0.5));
        shadow.setRadius(40);
        shadow.setSpread(0.05);
        card.setEffect(shadow);

        StackPane wrapper = new StackPane(card);
        wrapper.setPickOnBounds(false);
        VBox holder = new VBox(wrapper);
        holder.setAlignment(Pos.CENTER);
        holder.setFillWidth(false);
        return holder;
    }

    /** The phone number, shown as a pill with a phone icon. */
    private VBox buildContactBlock() {
        Label label = new Label("رقم الاتصال");
        label.setFont(Font.font("System", FontWeight.BOLD, 12));
        label.setTextFill(Color.web(TEXT_MUTED));

        FontIcon phoneIcon = new FontIcon("fas-phone-alt");
        phoneIcon.setIconSize(16);
        phoneIcon.setIconColor(Color.web("#5DA8FF"));

        // Kept LTR so the digits read in their natural order regardless of the
        // RTL scene orientation.
        Label number = new Label(LicenseService.CONTACT_PHONE);
        number.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
        number.setFont(Font.font("System", FontWeight.BOLD, 22));
        number.setTextFill(Color.WHITE);

        HBox pill = new HBox(10, phoneIcon, number);
        pill.setAlignment(Pos.CENTER);
        pill.setPadding(new Insets(12, 24, 12, 24));
        pill.setStyle(
                "-fx-background-color: rgba(255,255,255,0.06);" +
                        "-fx-background-radius: 16;" +
                        "-fx-border-color: rgba(255,255,255,0.10);" +
                        "-fx-border-radius: 16;" +
                        "-fx-border-width: 1;"
        );

        VBox block = new VBox(8, label, pill);
        block.setAlignment(Pos.CENTER);
        block.setPadding(new Insets(4, 0, 4, 0));
        return block;
    }

    private Pane buildBackground(double width, double height) {
        Pane container = new Pane();
        container.setPrefSize(width, height);
        container.setClip(new Rectangle(width, height));

        Image image = loadImage("/images/gym-background.jpg");
        if (image != null) {
            ImageView imageView = new ImageView(image);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            double scale = Math.max(width / image.getWidth(), height / image.getHeight());
            double fitW = image.getWidth() * scale;
            double fitH = image.getHeight() * scale;
            imageView.setFitWidth(fitW);
            imageView.setFitHeight(fitH);
            imageView.setLayoutX((width - fitW) / 2);
            imageView.setLayoutY((height - fitH) / 2);
            imageView.setEffect(new BoxBlur(10, 10, 3));
            container.getChildren().add(imageView);
        } else {
            Region fallback = new Region();
            fallback.setPrefSize(width, height);
            fallback.setStyle("-fx-background-color: linear-gradient(to bottom right, "
                    + BG_TOP + ", " + BG_BOTTOM + ");");
            container.getChildren().add(fallback);
        }

        Region overlay = new Region();
        overlay.setPrefSize(width, height);
        overlay.setStyle("-fx-background-color: linear-gradient(to bottom right, "
                + "rgba(6,8,13,0.82), rgba(4,5,9,0.88));");
        container.getChildren().add(overlay);

        return container;
    }

    private Image loadImage(String resourcePath) {
        try (java.io.InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                return null;
            }
            Image image = new Image(in);
            return image.isError() ? null : image;
        } catch (Exception ex) {
            return null;
        }
    }

    private void playEntrance() {
        new ParallelTransition(
                staggeredIn(titleLabel, 0),
                staggeredIn(bodyLabel, 110),
                staggeredIn(contactBlock, 220)
        ).play();
    }

    private ParallelTransition staggeredIn(Node node, int delayMillis) {
        FadeTransition fade = new FadeTransition(Duration.millis(480), node);
        fade.setToValue(1);
        fade.setInterpolator(Interpolator.EASE_OUT);

        TranslateTransition slide = new TranslateTransition(Duration.millis(480), node);
        slide.setFromY(14);
        slide.setToY(0);
        slide.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition in = new ParallelTransition(fade, slide);
        in.setDelay(Duration.millis(delayMillis));
        return in;
    }
}
