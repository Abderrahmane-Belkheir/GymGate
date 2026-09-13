package com.GymGate.UI.component;

import com.GymGate.bussines.services.I18nService;
import com.GymGate.model.NotificationData;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * The dropdown behind the header bell: a compact, newest-first list of the
 * notifications collected in {@code NotificationCenter}. Read-only — it is a
 * recap of what already flashed by as a toast.
 */
public final class NotificationListPopup {

    private final Popup popup = new Popup();

    public NotificationListPopup(ObservableList<NotificationData> items) {
        Label heading = new Label(I18nService.get("Notifications"));
        heading.getStyleClass().add("notif-popup-heading");

        ListView<NotificationData> list = new ListView<>(items);
        list.getStyleClass().add("notif-popup-list");
        list.setFocusTraversable(false);
        list.setPlaceholder(new Label(I18nService.get("No_notifications_yet")));
        list.setCellFactory(v -> new Row());
        list.setPrefWidth(360);
        list.setPrefHeight(380);
        VBox.setVgrow(list, Priority.ALWAYS);

        VBox panel = new VBox(heading, list);
        panel.getStyleClass().add("notif-popup");
        panel.setPadding(new Insets(14));
        panel.setSpacing(10);

        popup.getContent().add(panel);
        popup.setAutoHide(true);
        popup.setAutoFix(true);
    }

    /** Anchor the popup's top-right just under {@code anchor}'s bottom-right. */
    public void toggle(Node anchor) {
        if (popup.isShowing()) {
            popup.hide();
            return;
        }
        var b = anchor.localToScreen(anchor.getBoundsInLocal());
        popup.show(anchor, b.getMaxX() - 360, b.getMaxY() + 10);
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    private static final class Row extends ListCell<NotificationData> {
        @Override
        protected void updateItem(NotificationData n, boolean empty) {
            super.updateItem(n, empty);
            if (empty || n == null) {
                setText(null);
                setGraphic(null);
                return;
            }

            StackPane icon = new IconTile(n.getIconLiteral(), n.getIconBackgroundStyleClass(), 14);

            Label title = new Label(n.getTitle());
            title.getStyleClass().add("notification-title");
            Label subtitle = new Label(n.getSubtitle());
            subtitle.getStyleClass().add("notification-subtitle");
            subtitle.setWrapText(true);
            Label when = new Label(relative(n.getTime()));
            when.getStyleClass().add("notif-popup-time");

            VBox text = new VBox(1, title, subtitle, when);
            text.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(text, Priority.ALWAYS);

            HBox row = new HBox(11, icon, text);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("notification-row");
            row.setPadding(new Insets(6, 2, 6, 2));

            setGraphic(row);
        }

        private static String relative(LocalDateTime t) {
            if (t == null) {
                return "";
            }
            long mins = Duration.between(t, LocalDateTime.now()).toMinutes();
            if (mins < 1) {
                return I18nService.get("Just_now");
            }
            if (mins < 60) {
                return mins + " " + I18nService.get("minutes_ago_short");
            }
            long hours = mins / 60;
            if (hours < 24) {
                return hours + " " + I18nService.get("hours_ago_short");
            }
            return (hours / 24) + " " + I18nService.get("days_ago_short");
        }
    }
}
