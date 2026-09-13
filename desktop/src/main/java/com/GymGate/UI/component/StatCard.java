package com.GymGate.UI.component;

import com.GymGate.model.StatData;
import javafx.event.Event;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;


/**
 * A single tile inside the "Today's Statistics" panel. Fully data-driven
 * so it can be refreshed later with live values via updateValue(...).
 *
 * Two layouts:
 *  - Normal (default): icon top-left, value/label left-aligned below.
 *  - Wide/centered (pass true to the second constructor): used for a
 *    card that spans multiple grid columns - icon, value, and label are
 *    all centered so a full-width card doesn't look lopsided.
 *
 * Every card also carries a small refresh button pinned to its top-right
 * corner — see {@link #setOnRefresh}.
 */
public class StatCard extends VBox {

    private final Label valueLabel = new Label();
    private final Button refreshButton = new Button();
    private Runnable onActivate;
    private Runnable onRefresh;

    public StatCard(StatData data) {
        this(data, false);
    }

    public StatCard(StatData data, boolean centered) {
        getStyleClass().add("stat-card");
        if (centered) {
            getStyleClass().add("stat-card-wide");
        }
        setSpacing(14);

        IconTile iconTile = new IconTile(data.getIconLiteral(), data.getIconBackgroundStyleClass(), 18);

        FontIcon refreshIcon = new FontIcon("fas-sync-alt");
        refreshIcon.getStyleClass().add("stat-card-refresh-icon");
        refreshButton.setGraphic(refreshIcon);
        refreshButton.getStyleClass().add("stat-card-refresh-button");
        refreshButton.setFocusTraversable(false);
        refreshButton.setVisible(false); // hidden until setOnRefresh is wired
        refreshButton.setManaged(false);
        refreshButton.setOnAction(e -> {
            SpinAnimation.spinOnce(refreshIcon);
            if (onRefresh != null) {
                onRefresh.run();
            }
        });
        // The card itself reacts to clicks (drill-down) via setOnMouseClicked —
        // without this the button's click would bubble up and also open that.
        refreshButton.addEventFilter(MouseEvent.MOUSE_CLICKED, Event::consume);

        valueLabel.getStyleClass().add("stat-value");
        valueLabel.setText(data.getValue());

        Label descriptionLabel = new Label(data.getLabel());
        descriptionLabel.getStyleClass().add("stat-label");

        StackPane topRow = new StackPane(iconTile, refreshButton);
        StackPane.setAlignment(refreshButton, Pos.TOP_RIGHT);

        if (centered) {
            setAlignment(Pos.CENTER);
            StackPane.setAlignment(iconTile, Pos.TOP_CENTER);

            VBox textBox = new VBox(2, valueLabel, descriptionLabel);
            textBox.setAlignment(Pos.CENTER);
            valueLabel.setAlignment(Pos.CENTER);
            descriptionLabel.setAlignment(Pos.CENTER);

            getChildren().addAll(topRow, textBox);
        } else {
            StackPane.setAlignment(iconTile, Pos.CENTER_LEFT);

            VBox textBox = new VBox(2, valueLabel, descriptionLabel);

            getChildren().addAll(topRow, textBox);
        }
    }

    public void incrementValue(int value) {
        int pre = Integer.parseInt(valueLabel.getText());
        valueLabel.setText(String.valueOf(pre + value));
        refreshActivatableState();
    }

    /** Sets the absolute value, replacing whatever was there — used after a
     *  manual refresh, so it corrects any drift instead of compounding it. */
    public void setValue(int value) {
        valueLabel.setText(String.valueOf(value));
        refreshActivatableState();
    }

    /**
     * Makes the tile behave as a button: a click runs {@code action} (used on
     * Home to drill the card open into the list behind its number). The card
     * only reacts while its value is above zero - an empty stat has no list
     * to show, so it stays inert and drops the hand cursor / hover accent.
     * Safe to call more than once; re-evaluated on every incrementValue().
     */
    public void setOnActivate(Runnable action) {
        this.onActivate = action;
        setOnMouseClicked(e -> {
            if (onActivate != null && currentValue() > 0) {
                onActivate.run();
            }
        });
        refreshActivatableState();
    }

    /**
     * Reveals the corner refresh button and wires it to {@code action} — call
     * this with a recompute of just this tile's number (e.g. re-running the
     * one {@code StatsService} query behind it) so a receptionist can pull a
     * stale-looking tile back in sync without waiting for the next event that
     * would normally update it.
     */
    public void setOnRefresh(Runnable action) {
        this.onRefresh = action;
        refreshButton.setVisible(true);
        refreshButton.setManaged(true);
    }

    private int currentValue() {
        try {
            return Integer.parseInt(valueLabel.getText().trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void refreshActivatableState() {
        boolean active = onActivate != null && currentValue() > 0;
        setCursor(active ? Cursor.HAND : Cursor.DEFAULT);
        getStyleClass().remove("stat-card-clickable");
        if (active) {
            getStyleClass().add("stat-card-clickable");
        }
    }
}
