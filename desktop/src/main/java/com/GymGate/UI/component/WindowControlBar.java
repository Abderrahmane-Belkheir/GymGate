package com.GymGate.UI.component;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * Custom replacement for the native title-bar window controls
 * (minimize / maximize-restore / close). Required because the
 * primary Stage runs UNDECORATED, so the OS no longer draws its
 * own title bar buttons.
 */
public class WindowControlBar extends HBox {

    public WindowControlBar(Stage stage) {
        getStyleClass().add("window-controls");
        setSpacing(6);
        setAlignment(Pos.CENTER_RIGHT);

        Button minimizeBtn = buildButton("fas-window-minimize", "window-btn");
        minimizeBtn.setOnAction(e -> stage.setIconified(true));

        Button maximizeBtn = buildButton("fas-window-maximize", "window-btn");
        maximizeBtn.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));

        Button closeBtn = buildButton("fas-times", "window-btn-close");
        closeBtn.setOnAction(e -> stage.close());

        getChildren().addAll(minimizeBtn, maximizeBtn, closeBtn);
    }

    private Button buildButton(String iconLiteral, String styleClass) {
        Button button = new Button();
        button.getStyleClass().add(styleClass);
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("window-btn-icon");
        button.setGraphic(icon);
        button.setFocusTraversable(false);
        return button;
    }
}
