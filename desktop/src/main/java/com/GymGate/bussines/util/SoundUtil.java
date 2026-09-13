package com.GymGate.bussines.util;

import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.net.URL;

public final class SoundUtil {

    private static MediaPlayer welcomePlayer;
    private static MediaPlayer noActivePlanPlayer;
    private static MediaPlayer membershipExpiredPlayer;
    private static MediaPlayer whatsappWhistlePlayer;
    private static MediaPlayer notificationPlayer;
    private static MediaPlayer cameraShutterPlayer;

    private SoundUtil() {
    }

    public static void initialize() {
        welcomePlayer = createPlayer("/sounds/hello.wav");
        noActivePlanPlayer = createPlayer("/sounds/no_active_plan.wav");
        membershipExpiredPlayer = createPlayer("/sounds/membership_ended.wav");
        whatsappWhistlePlayer = createPlayer("/sounds/whatsapp_whistle.wav");
        // The clip is long; only ever play its opening.
        whatsappWhistlePlayer.setStopTime(Duration.seconds(3));
        notificationPlayer = createPlayer("/sounds/notification.wav");
        cameraShutterPlayer = createPlayer("/sounds/camera_shutter.wav");
    }

    private static MediaPlayer createPlayer(String resourcePath) {
        URL resource = SoundUtil.class.getResource(resourcePath);

        if (resource == null) {
            throw new IllegalStateException(
                    "Sound resource not found: " + resourcePath
            );
        }

        return new MediaPlayer(
                new Media(resource.toExternalForm())
        );
    }

    public static void playWelcome() {
        play(welcomePlayer);
    }

    public static void playNoActivePlan() {
        play(noActivePlanPlayer);
    }

    public static void playMembershipExpired() {
        play(membershipExpiredPlayer);
    }

    /** Plays only the first 3 seconds (see {@link #initialize()}). */
    public static void playWhatsappWhistle() {
        play(whatsappWhistlePlayer);
    }

    /** Short chime for an in-app notification (sync pull, etc.). */
    public static void playNotification() {
        play(notificationPlayer);
    }

    /** Shutter click for the registration/update photo-capture step. */
    public static void playCameraShutter() {
        play(cameraShutterPlayer);
    }

    private static void play(MediaPlayer player) {
        if (player == null) {
            throw new IllegalStateException(
                    "SoundUtil has not been initialized."
            );
        }
        System.out.println("playing sound");
        Platform.runLater(() -> {
            player.stop();
            player.seek(Duration.ZERO);
            player.play();
        });
    }
}
