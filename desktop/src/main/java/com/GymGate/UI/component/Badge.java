package com.GymGate.UI.component;

import javafx.scene.control.Label;

/**
 * Small rounded pill label used to show statuses such as
 * "Active" or "Verified".
 */
public class Badge extends Label {

    public Badge(String text, String variantStyleClass) {
        super(text);
        getStyleClass().addAll("badge", variantStyleClass);
    }
}
