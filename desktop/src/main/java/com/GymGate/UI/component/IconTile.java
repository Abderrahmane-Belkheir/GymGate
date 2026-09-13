package com.GymGate.UI.component;

import javafx.scene.layout.StackPane;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * A rounded, colored square/circle housing a single Ikonli icon.
 * Reused by stat cards, notification rows and info chips.
 */
public class IconTile extends StackPane {

    public IconTile(String iconLiteral, String backgroundStyleClass) {
        this(iconLiteral, backgroundStyleClass, 16);
    }

    public IconTile(String iconLiteral, String backgroundStyleClass, double iconSize) {
        getStyleClass().addAll("icon-tile", backgroundStyleClass);
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize((int) iconSize);
        icon.getStyleClass().add("icon-tile-glyph");
        getChildren().add(icon);
    }
}
