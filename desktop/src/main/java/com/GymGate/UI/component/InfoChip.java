package com.GymGate.UI.component;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Small rounded chip used on the member info card to show a
 * labeled attribute, e.g. "PLAN / Premium Annual".
 */
public class InfoChip extends HBox {

    private final Label valueLabel;

    public InfoChip(String iconLiteral, String caption, String value) {
        getStyleClass().add("info-chip");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(10);

        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("info-chip-icon");

        Label captionLabel = new Label(caption);
        captionLabel.getStyleClass().add("info-chip-caption");

        valueLabel = new Label(value);
        valueLabel.getStyleClass().add("info-chip-value");

        VBox textBox = new VBox(2, captionLabel, valueLabel);
        getChildren().addAll(icon, textBox);
    }

    public void setValue(String value) {
        valueLabel.setText(value);
    }
}
