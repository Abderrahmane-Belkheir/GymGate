package com.GymGate.UI.navigation;

import com.GymGate.UI.controller.SettingsController;
import com.GymGate.UI.controller.SidebarController;
import com.GymGate.bussines.services.I18nService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Owns the page cache for the application shell.
 *
 * Each {@link Page}'s FXML is loaded at most once, lazily on the first
 * {@link #show(Page)}. The resulting {@link Parent} and its controller are
 * then cached, so switching back to a page never re-parses FXML or re-runs a
 * controller's initialize() — it just swaps the cached node into the content
 * area. Pages the user never opens in a session are never built, so their
 * scene graphs cost no memory.
 *
 * Deliberately plain: no DI framework, no event bus, no reflection beyond
 * what FXMLLoader already does.
 */
public class NavigationManager {

    private final Pane contentArea;
    private final Map<Page, Parent> pageRoots = new EnumMap<>(Page.class);
    private final Map<Page, Object> pageControllers = new EnumMap<>(Page.class);


    public NavigationManager(Pane contentArea,SidebarController sidebarController) {
        this.contentArea = contentArea;
    }

    /** Displays the given page in the content area, loading it on first use. */
    public void show(Page page) {
        loadIfAbsent(page);
        contentArea.getChildren().setAll(pageRoots.get(page));
    }

    /** Returns the cached controller for a page (only valid after it has been loaded). */
    @SuppressWarnings("unchecked")
    public <T> T getController(Page page) {
        return (T) pageControllers.get(page);
    }

    private void loadIfAbsent(Page page) {
        if (pageRoots.containsKey(page)) {
            return;
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(page.getFxmlPath()));
            loader.setResources(I18nService.getBundle());
            Parent root = loader.load();
            pageRoots.put(page, root);
            pageControllers.put(page, loader.getController());
        } catch (IOException e) {e.printStackTrace();
          //  throw new UncheckedIOException("Failed to load page: " + page, e);
        }
    }
}
