package com.GymGate.UI.component;

import javafx.animation.AnimationTimer;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;

/** Small reusable helper for the blur + dark-overlay look used behind modal dialogs. */
public final class DialogEffects {

    private DialogEffects() {
    }

    private static final double BLUR_RADIUS = 12;
    private static final double DIM_BRIGHTNESS = -0.22;
    /** Key under which the running in/out animation is stashed on the blurred node. */
    private static final String ANIM_KEY = "gg.dialog.blurAnim";

    /**
     * Blurs and darkens {@code node} for a modal dialog — <b>animated in</b> from
     * nothing over ~140&nbsp;ms rather than snapping, so opening a dialog reads as
     * one smooth reveal instead of a harsh flash. Starting the blur at radius 0
     * also avoids the one dropped frame a full-window 12&nbsp;px blur would cost
     * if applied instantly.
     */
    public static void applyBlurAndDim(Node node) {
        stopAnim(node);

        GaussianBlur blur = new GaussianBlur(0);
        ColorAdjust dim = new ColorAdjust();
        dim.setBrightness(0);
        dim.setInput(blur);
        node.setEffect(dim);

        Timeline in = new Timeline(new KeyFrame(Duration.millis(140),
                new KeyValue(blur.radiusProperty(), BLUR_RADIUS, Interpolator.EASE_OUT),
                new KeyValue(dim.brightnessProperty(), DIM_BRIGHTNESS, Interpolator.EASE_OUT)));
        node.getProperties().put(ANIM_KEY, in);
        in.play();
    }

    /** Fades the blur/dim back out over ~110&nbsp;ms, then removes the effect. */
    public static void clear(Node node) {
        stopAnim(node);
        if (!(node.getEffect() instanceof ColorAdjust dim)
                || !(dim.getInput() instanceof GaussianBlur blur)) {
            node.setEffect(null);
            return;
        }
        Timeline out = new Timeline(new KeyFrame(Duration.millis(110),
                new KeyValue(blur.radiusProperty(), 0, Interpolator.EASE_OUT),
                new KeyValue(dim.brightnessProperty(), 0, Interpolator.EASE_OUT)));
        out.setOnFinished(e -> {
            if (node.getEffect() == dim) {
                node.setEffect(null);
            }
        });
        node.getProperties().put(ANIM_KEY, out);
        out.play();
    }

    private static void stopAnim(Node node) {
        Object a = node.getProperties().remove(ANIM_KEY);
        if (a instanceof Timeline t) {
            t.stop();
        }
    }

    /**
     * Works around a JavaFX StageStyle.TRANSPARENT quirk: the very first frame of a
     * transparent stage is often composited against an opaque backing surface before
     * the OS finishes establishing true per-pixel transparency. Since our dialog cards
     * use CSS -fx-background-radius rather than an actual window clip, that first
     * frame shows up as a brief flash of solid color right at the rounded corners
     * (and along the drop-shadow edges) every time a dialog opens.
     * <p>
     * {@code Stage.setOpacity(0)} alone does NOT hide this on Windows: a
     * StageStyle.TRANSPARENT window is composited via UpdateLayeredWindow for its
     * per-pixel alpha, which ignores the plain window-alpha attribute opacity relies
     * on, so the flash still renders at full visibility. Moving the stage off the
     * visible screen for its first frames masks it regardless of platform; snapping
     * it to centre AND fading the content in on the same pulse — once the peer's
     * per-pixel alpha is established — makes the reveal invisible to the user.
     * <p>
     * Call this right after {@code dialogStage.setScene(...)}, before the first
     * {@code show()}/{@code showAndWait()}. Dialogs that reuse one Stage across many
     * opens are handled too: this arms itself via WINDOW_SHOWING/WINDOW_SHOWN
     * handlers (every show gets a fresh native peer on Windows). addEventHandler is
     * used so a controller's own setOnShowing/setOnHidden can't overwrite it.
     */
    public static void preventShowFlash(Stage dialogStage) {
        dialogStage.setX(-32_000);
        dialogStage.setY(-32_000);

        dialogStage.addEventHandler(WindowEvent.WINDOW_SHOWING, e -> {
            Parent root = rootOf(dialogStage);
            if (root != null) {
                // First CSS + layout pass now, synchronously, so the stage
                // auto-sizes correctly and nothing is measured lazily on the
                // pulse the window first appears.
                root.applyCss();
                root.layout();
                root.setOpacity(0);          // content hidden until we fade it in
            }
            dialogStage.setX(-32_000);
            dialogStage.setY(-32_000);
        });

        dialogStage.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> new AnimationTimer() {
            private int frames = 0;

            @Override
            public void handle(long now) {
                // A couple of frames for the transparent peer's alpha to settle,
                // then centre and fade in together — no empty transparent window
                // ever lingers at the centre position.
                if (++frames < 3) {
                    return;
                }
                stop();
                dialogStage.centerOnScreen();
                Parent root = rootOf(dialogStage);
                if (root != null) {
                    FadeTransition fade = new FadeTransition(Duration.millis(130), root);
                    fade.setFromValue(0);
                    fade.setToValue(1);
                    fade.play();
                }
            }
        }.start());
    }

    private static Parent rootOf(Stage stage) {
        Scene scene = stage.getScene();
        return scene == null ? null : scene.getRoot();
    }

    /**
     * Wraps a dialog's loaded FXML root in a plain StackPane so it can be used as the
     * Scene's actual root, with that outer node's background forced transparent via an
     * INLINE style rather than a stylesheet class.
     * <p>
     * Reason: the Scene automatically tags whatever node is passed to {@code new
     * Scene(root)} with JavaFX's implicit "root" style class. If that node's own
     * transparent/colored background comes from an external stylesheet (like
     * ".register-dialog-card" in styles.css), it only takes effect once the first CSS
     * pass runs — which can land on the same pulse the window is first painted. Until
     * then the node falls back to Modena's default opaque ".root" background across
     * its full rectangular bounds, which shows through right at the rounded card's
     * corners as a flash. An inline style (set here, in Java) applies immediately, with
     * no CSS-parse race, so the true window background is guaranteed transparent from
     * the very first frame. The original dialogRoot is kept as an unmodified child, so
     * its own styling (background, radius, drop shadow) renders exactly as before.
     */
    public static Parent wrapForTransparentScene(Parent dialogRoot) {
        StackPane wrapper = new StackPane(dialogRoot);
        wrapper.setStyle("-fx-background-color: transparent;");
        return wrapper;
    }
}
