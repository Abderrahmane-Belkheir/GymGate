package com.GymGate.UI.controller;


import com.GymGate.UI.NotificationCenter;
import com.GymGate.UI.component.NotificationListPopup;
import com.GymGate.UI.component.NotificationToast;
import com.GymGate.UI.component.WindowControlBar;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controls the top header bar: page title, live clock and the
 * receptionist's account chip. The clock/date are placeholders that
 * tick using the local system clock; wall-clock display only, no
 * business logic.
 *
 * Because the primary Stage runs UNDECORATED, this header also takes
 * over the two jobs the native title bar used to do: letting the user
 * drag the window, and hosting minimize/maximize/close buttons.
 */
public class HeaderController {

    @FXML
    private HBox headerRoot;

    @FXML
    private HBox windowControlsContainer;

    @FXML
    private Label clockLabel;

    @FXML
    private Label dateLabel;

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("hh:mm:ss a");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, MMMM d");

    private double dragOffsetX;
    private double dragOffsetY;

    @FXML private ToggleButton cameraVisibilityToggle;
    @FXML private FontIcon cameraVisibilityIcon;

    @FXML private StackPane notificationBell;
    @FXML private StackPane notificationDot;

    private final BooleanProperty cameraVisible = new SimpleBooleanProperty(true);

    private NotificationListPopup notificationList;

    @FXML
    public void initialize() {
        updateClock();
        Timeline timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> updateClock()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
        cameraVisibilityToggle.selectedProperty().bindBidirectional(cameraVisible);
        cameraVisible.addListener((obs, wasVisible, isVisible) ->
                cameraVisibilityIcon.setIconLiteral(isVisible ? "fas-eye" : "fas-eye-slash"));

        // The red dot only shows while there are notifications the user hasn't
        // looked at — opening the bell clears it.
        NotificationCenter center = NotificationCenter.getInstance();
        notificationDot.visibleProperty().bind(center.unreadProperty().greaterThan(0));

        notificationList = new NotificationListPopup(center.getItems());
        notificationBell.setOnMouseClicked(e -> {
            notificationList.toggle(notificationBell);
            if (notificationList.isShowing()) {
                center.markAllRead();
            }
        });
    }

    /**
     * Wires this header up to the given Stage: adds the custom
     * minimize/maximize/close buttons and enables click-and-drag
     * window movement. Call this once from MainApp after the Stage
     * is created, e.g. {@code headerController.attachToStage(primaryStage);}
     */
    public void attachToStage(Stage stage) {
        windowControlsContainer.getChildren().add(new WindowControlBar(stage));

        // One toast presenter for the life of the window; NotificationCenter
        // feeds it every push, from any thread.
        NotificationToast toast = new NotificationToast(stage, notificationBell);
        NotificationCenter.getInstance().onToast(toast::show);

        headerRoot.setOnMousePressed(event -> {
            // Only start a drag when the header's own background was
            // clicked, so buttons/labels inside it (bell, user chip,
            // window controls) keep working normally.
            if (event.getTarget() == headerRoot) {
                dragOffsetX = stage.getX() - event.getScreenX();
                dragOffsetY = stage.getY() - event.getScreenY();
            } else {
                dragOffsetX = Double.NaN;
            }
        });
        headerRoot.setOnMouseDragged(event -> {
            if (!Double.isNaN(dragOffsetX)) {
                stage.setX(event.getScreenX() + dragOffsetX);
                stage.setY(event.getScreenY() + dragOffsetY);
            }
        });
    }

    private void updateClock() {
        LocalDateTime now = LocalDateTime.now();
        clockLabel.setText(now.format(TIME_FORMAT));
        dateLabel.setText(now.format(DATE_FORMAT));
    }

    public BooleanProperty cameraVisibleProperty() {
        return cameraVisible;
    }

}
