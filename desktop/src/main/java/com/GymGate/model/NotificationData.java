package com.GymGate.model;

import java.time.LocalDateTime;

/**
 * Immutable data holder describing a single notification — shown briefly as a
 * toast at the top of the window and then kept in the header bell's list.
 */
public class NotificationData {

    private final String iconLiteral;
    private final String iconBackgroundStyleClass;
    private final String title;
    private final String subtitle;
    private final LocalDateTime time;

    public NotificationData(String iconLiteral, String iconBackgroundStyleClass, String title, String subtitle) {
        this(iconLiteral, iconBackgroundStyleClass, title, subtitle, LocalDateTime.now());
    }

    public NotificationData(String iconLiteral, String iconBackgroundStyleClass, String title, String subtitle,
                            LocalDateTime time) {
        this.iconLiteral = iconLiteral;
        this.iconBackgroundStyleClass = iconBackgroundStyleClass;
        this.title = title;
        this.subtitle = subtitle;
        this.time = time;
    }

    public String getIconLiteral() { return iconLiteral; }
    public String getIconBackgroundStyleClass() { return iconBackgroundStyleClass; }
    public String getTitle() { return title; }
    public String getSubtitle() { return subtitle; }
    public LocalDateTime getTime() { return time; }
}
