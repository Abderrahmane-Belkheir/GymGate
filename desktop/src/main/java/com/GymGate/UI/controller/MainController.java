package com.GymGate.UI.controller;

import com.GymGate.UI.navigation.NavigationManager;
import com.GymGate.UI.navigation.Page;
import javafx.fxml.FXML;
import javafx.scene.layout.StackPane;

/**
 * Controller for the application shell (MainView.fxml).
 *
 * Owns the {@link NavigationManager} (page loading + caching) and wires
 * the sidebar's navigation requests to it. Beyond that, this shell only
 * composes the sidebar, header and content area via fx:include — each
 * sub-controller (header, sidebar, individual pages) stays independent
 * of the others.
 */
public class MainController {

    // FXMLLoader auto-injects the nested controller for fx:include
    // elements using the "<fx:id>Controller" naming convention.
    @FXML private HeaderController headerController;
    @FXML private SidebarController sidebarController;

    @FXML private StackPane contentArea;

    private NavigationManager navigationManager;

    @FXML
    public void initialize() {
        navigationManager = new NavigationManager(contentArea,sidebarController);
        sidebarController.setOnNavigate(navigationManager::show);

        // Home is displayed on startup; other pages build lazily on first visit,
        // so a session that only touches Home never pays for their scene graphs.
        navigationManager.show(Page.HOME);

        HomeController homeController = navigationManager.getController(Page.HOME);
        headerController.cameraVisibleProperty().addListener((obs, wasVisible, isVisible) ->
                homeController.setCameraVisible(isVisible));
    }

    public HeaderController getHeaderController() {
        return headerController;
    }
}
