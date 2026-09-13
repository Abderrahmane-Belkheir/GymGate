package com.GymGate.UI.component;

import com.GymGate.model.NotificationData;
import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.SequentialTransition;
import javafx.animation.TranslateTransition;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Window;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The transient card that slides in just below the header, holds briefly, then
 * shrinks and flies into the header bell. The same {@link NotificationData} it
 * shows is already sitting in the bell's list by the time it lands, so nothing
 * is lost if the user isn't looking.
 *
 * <p>One instance per window, owned by the header. Several notifications in
 * quick succession queue up and play one after another — with a shorter hold
 * while a backlog is waiting so a burst clears fast.
 */
public final class NotificationToast {

    private static final Duration IN = Duration.millis(240);
    private static final Duration HOLD = Duration.seconds(4);
    private static final Duration HOLD_BURST = Duration.millis(1500);
    private static final Duration FLY = Duration.millis(460);
    private static final double DROP = 14; // px it travels on the way in

    private final Window owner;
    private final Node bell;
    private final Popup popup = new Popup();
    private final HBox card = new HBox(12);
    private final StackPane iconHolder = new StackPane();
    private final Label title = new Label();
    private final Label subtitle = new Label();

    private final Deque<NotificationData> queue = new ArrayDeque<>();
    private SequentialTransition running;

    public NotificationToast(Window owner, Node bell) {
        this.owner = owner;
        this.bell = bell;

        title.getStyleClass().add("toast-title");
        subtitle.getStyleClass().add("toast-subtitle");
        subtitle.setWrapText(true);
        VBox text = new VBox(2, title, subtitle);
        text.setAlignment(Pos.CENTER_LEFT);

        card.getStyleClass().add("toast");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMaxWidth(420);
        card.getChildren().addAll(iconHolder, text);

        popup.getContent().add(card);
        popup.setAutoHide(false);
        popup.setAutoFix(false);
    }

    /** Enqueue {@code n}; it plays now if nothing else is on screen. */
    public void show(NotificationData n) {
        queue.addLast(n);
        if (running == null) {
            playNext();
        }
    }

    private void playNext() {
        NotificationData n = queue.pollFirst();
        if (n == null) {
            running = null;
            return;
        }
        if (owner == null || !owner.isShowing()) {
            queue.clear();
            running = null;
            return;
        }

        title.setText(n.getTitle());
        subtitle.setText(n.getSubtitle());
        iconHolder.getChildren().setAll(
                new IconTile(n.getIconLiteral(), n.getIconBackgroundStyleClass(), 15));

        card.setOpacity(0);
        card.setScaleX(1);
        card.setScaleY(1);
        card.setTranslateX(0);
        card.applyCss();
        card.layout();

        double cardWidth = card.prefWidth(-1);
        double cardHeight = card.prefHeight(cardWidth);
        double x = owner.getX() + (owner.getWidth() - cardWidth) / 2;
        double y = owner.getY() + 90; // just clear of the header
        popup.hide();
        popup.show(owner, x, y);

        card.setTranslateY(-DROP);

        TranslateTransition slideIn = new TranslateTransition(IN, card);
        slideIn.setToY(0);
        slideIn.setInterpolator(Interpolator.EASE_OUT);
        FadeTransition fadeIn = new FadeTransition(IN, card);
        fadeIn.setToValue(1);

        PauseTransition hold = new PauseTransition(queue.isEmpty() ? HOLD : HOLD_BURST);

        // Fly into the bell: translate toward it, shrink, fade.
        double toX = 0;
        double toY = -DROP;
        Bounds bellScreen = bell == null ? null : bell.localToScreen(bell.getBoundsInLocal());
        if (bellScreen != null) {
            double cardCenterX = x + cardWidth / 2;
            double cardCenterY = y + cardHeight / 2;
            toX = (bellScreen.getMinX() + bellScreen.getWidth() / 2) - cardCenterX;
            toY = (bellScreen.getMinY() + bellScreen.getHeight() / 2) - cardCenterY;
        }
        TranslateTransition fly = new TranslateTransition(FLY, card);
        fly.setToX(toX);
        fly.setToY(toY);
        fly.setInterpolator(Interpolator.EASE_IN);
        ScaleTransition shrink = new ScaleTransition(FLY, card);
        shrink.setToX(0.18);
        shrink.setToY(0.18);
        shrink.setInterpolator(Interpolator.EASE_IN);
        FadeTransition fadeOut = new FadeTransition(FLY, card);
        fadeOut.setToValue(0);
        fadeOut.setInterpolator(Interpolator.EASE_IN);

        running = new SequentialTransition(
                new ParallelTransition(slideIn, fadeIn),
                hold,
                new ParallelTransition(fly, shrink, fadeOut));
        running.setOnFinished(e -> {
            popup.hide();
            playNext();
        });
        running.playFromStart();
    }
}
