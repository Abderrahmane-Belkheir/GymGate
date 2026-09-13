package com.GymGate.bussines.services;

import javafx.application.Platform;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Tiny in-app pub/sub so the always-loaded screens (NavigationManager preloads
 * every page and keeps it alive) refresh when data is mutated elsewhere — a
 * check-in on Home, a registration or a renewal dialog. Listeners always run on
 * the JavaFX Application Thread.
 *
 * Not for cross-process / cross-device updates — that is what the Supabase sync
 * is for. This is purely "another tab in this same app just changed something".
 */
public final class AppEvents {

    public enum Type {
        MEMBER_ADDED,
        MEMBER_UPDATED,
        MEMBER_DELETED,
        ATTENDANCE_ADDED,
        PAYMENT_ADDED,
        /** A plan (or a séance price) changed — locally or pulled from the cloud. */
        PLAN_UPDATED,
        /** Gym display name and/or logo changed in the Gym Info dialog. */
        GYM_INFO_UPDATED
    }

    private static final Map<Type, List<Runnable>> LISTENERS = new EnumMap<>(Type.class);

    static {
        for (Type t : Type.values()) {
            LISTENERS.put(t, new CopyOnWriteArrayList<>());
        }
    }

    private AppEvents() {
    }

    /** Register a handler. Handlers are never removed — the controllers that
     *  subscribe live for the whole session. */
    public static void subscribe(Type type, Runnable onEvent) {
        LISTENERS.get(type).add(onEvent);
    }

    /** Fire one or more events; each handler runs on the FX thread. A throwing
     *  handler is logged and does not stop the others. */
    public static void publish(Type... types) {
        Runnable dispatch = () -> {
            for (Type type : types) {
                for (Runnable handler : LISTENERS.get(type)) {
                    try {
                        handler.run();
                    } catch (Exception e) {
                        System.err.println("AppEvents handler for " + type + " failed: " + e.getMessage());
                    }
                }
            }
        };
        if (Platform.isFxApplicationThread()) {
            dispatch.run();
        } else {
            Platform.runLater(dispatch);
        }
    }
}
