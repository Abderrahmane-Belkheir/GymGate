package com.GymGate.UI.component;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Rounded pill-shaped action button used in the top action bar,
 * e.g. "Register Member", "Search Member", "Renew Membership",
 * "Register Face". Supports a "primary" (filled/blue) visual variant.
 */
public class PillActionButton extends ToggleButton {

    public PillActionButton(String iconLiteral, String label, boolean selectedByDefault) {
        getStyleClass().add("pill-action-button");
        setFocusTraversable(false);

        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("pill-icon");

        Label textLabel = new Label(label);
        textLabel.getStyleClass().add("pill-label");

        HBox content = new HBox(10, icon, textLabel);
        content.setAlignment(Pos.CENTER);

        setGraphic(content);
        setText(null);
        setSelected(selectedByDefault);
    }
}
