package com.GymGate.UI.component;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A single selectable entry in the sidebar navigation
 * (e.g. Home, Members, Payments...).
 */
public class SidebarNavItem extends ToggleButton {

    public SidebarNavItem(String iconLiteral, String label) {
        getStyleClass().add("sidebar-nav-item");
        setMaxWidth(Double.MAX_VALUE);
        setAlignment(Pos.CENTER_LEFT);

        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("sidebar-nav-icon");

        Label textLabel = new Label(label);
        textLabel.getStyleClass().add("sidebar-nav-label");

        HBox content = new HBox(12, icon, textLabel);
        content.setAlignment(Pos.CENTER_LEFT);
        content.setPadding(new Insets(0));

        setGraphic(content);
        setText(null);
        setFocusTraversable(false);
    }
}
