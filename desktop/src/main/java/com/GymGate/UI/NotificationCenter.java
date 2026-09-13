package com.GymGate.UI;

import com.GymGate.bussines.util.SoundUtil;
import com.GymGate.model.NotificationData;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Process-wide sink for user-facing notifications. Anything in the app (a sync
 * pull, a background job) calls {@link #push} with a {@link NotificationData};
 * the header wires itself up once via {@link #onToast} to flash it at the top of
 * the window, and binds the bell to {@link #getItems()} / {@link #unreadProperty()}
 * for the persisted list.
 *
 * <p>Safe to call from any thread — every mutation is marshalled onto the FX
 * thread, so callers never touch {@code Platform.runLater} themselves.
 */
public final class NotificationCenter {

    private static final NotificationCenter INSTANCE = new NotificationCenter();

    public static NotificationCenter getInstance() {
        return INSTANCE;
    }

    /** The bell only keeps the most recent entries — it is a glance, not a log. */
    private static final int MAX_ITEMS = 30;

    private final ObservableList<NotificationData> items = FXCollections.observableArrayList();
    private final ReadOnlyIntegerWrapper unread = new ReadOnlyIntegerWrapper(0);
    private final List<Consumer<NotificationData>> toastHandlers = new CopyOnWriteArrayList<>();

    private NotificationCenter() {
    }

    /** Newest first. Bind a ListView to this. */
    public ObservableList<NotificationData> getItems() {
        return items;
    }

    /** Count of notifications received since the bell was last opened. */
    public ReadOnlyIntegerProperty unreadProperty() {
        return unread.getReadOnlyProperty();
    }

    /** Register a toast presenter. Called once by the header. */
    public void onToast(Consumer<NotificationData> handler) {
        toastHandlers.add(handler);
    }

    /** Clear the unread badge — the header calls this when the bell is opened. */
    public void markAllRead() {
        runFx(() -> unread.set(0));
    }

    /** Add a notification: prepend to the bell list, bump the unread badge, and
     *  fire the toast. No-op-safe from a background thread. */
    public void push(NotificationData notification) {
        runFx(() -> {
            items.add(0, notification);
            while (items.size() > MAX_ITEMS) {
                items.remove(items.size() - 1);
            }
            unread.set(unread.get() + 1);
            try {
                SoundUtil.playNotification();
            } catch (RuntimeException e) {
                System.err.println("Notification sound skipped: " + e.getMessage());
            }
            for (Consumer<NotificationData> handler : toastHandlers) {
                try {
                    handler.accept(notification);
                } catch (RuntimeException e) {
                    System.err.println("Toast handler failed: " + e.getMessage());
                }
            }
        });
    }

    private static void runFx(Runnable r) {
        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }
}
