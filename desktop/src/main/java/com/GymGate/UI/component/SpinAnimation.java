package com.GymGate.UI.component;

import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

/**
 * One shared "spin the icon" feedback animation for refresh-style controls —
 * a single clockwise turn — so every refresh button in the app (the per-card
 * corner button, the panel-wide one) looks and feels the same.
 */
public final class SpinAnimation {

    private SpinAnimation() {
    }

    private static final Duration DURATION = Duration.millis(550);
    /** Key the running transition is stashed under on the node, so a second
     *  click mid-spin restarts cleanly instead of fighting the first one. */
    private static final String ANIM_KEY = "gg.spin.anim";

    /** Spins {@code icon} 360° once. Safe to call again before the previous
     *  spin finished — it stops that one and restarts from scratch. */
    public static void spinOnce(Node icon) {
        Object running = icon.getProperties().get(ANIM_KEY);
        if (running instanceof RotateTransition previous) {
            previous.stop();
        }

        RotateTransition spin = new RotateTransition(DURATION, icon);
        spin.setByAngle(360);
        spin.setInterpolator(Interpolator.EASE_BOTH);
        spin.setCycleCount(1);
        spin.setOnFinished(e -> icon.getProperties().remove(ANIM_KEY));
        icon.getProperties().put(ANIM_KEY, spin);
        spin.playFromStart();
    }
}
