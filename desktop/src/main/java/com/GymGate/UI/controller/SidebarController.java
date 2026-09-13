package com.GymGate.UI.controller;

import com.GymGate.UI.navigation.Page;
import com.GymGate.UI.component.SidebarNavItem;
import com.GymGate.bussines.models.Settings;
import com.GymGate.bussines.services.AppEvents;
import com.GymGate.bussines.services.I18nService;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Builds the left navigation menu and emits navigation requests.
 *
 * This controller ONLY knows about labels/icons and, where one exists,
 * the page a given item navigates to. It never touches FXML,
 * never loads a page, and never reaches into the center content area —
 * it just calls the handler registered via {@link #setOnNavigate}.
 *
 * Members, Plans, Payments, Attendance, Reports and Settings don't have
 * a real page yet, so their entries pass {@code null} for now: they still
 * render and can be selected (matching current behavior), they just don't
 * navigate anywhere. Once a page exists, give it a Page entry here.
 */
public class SidebarController {

    @FXML private VBox navContainer;
    @FXML private Label gymNameLabel;
    @FXML private StackPane brandLogoFrame;
    @FXML private FontIcon brandPlaceholderIcon;
    @FXML private ImageView brandLogoImage;
    private final ToggleGroup navGroup = new ToggleGroup();

    private Consumer<Page> onNavigate = page -> { };

    private static final NavEntry[] NAV_ITEMS = {
            new NavEntry("fas-th-large", I18nService.get("Home"), Page.HOME),
            new NavEntry("fas-users", I18nService.get("Members"), Page.MEMBERS),
            new NavEntry("fas-id-card", I18nService.get("Plans"), Page.PLANS),
            new NavEntry("fas-credit-card", I18nService.get("Payments"), Page.PAYMENTS),
            new NavEntry("fas-calendar-check", I18nService.get("Attendance"), Page.ATTENDANCE),
            new NavEntry("fas-chart-bar", I18nService.get("Reports"), Page.REPORTS),
            new NavEntry("fas-cog", I18nService.get("Settings"), Page.SETTINGS)
    };

    @FXML
    public void initialize() {
        // Clip the logo image to a circle matching the 34px badge.
        brandLogoImage.setClip(new Circle(17, 17, 17));
        renderBrand();
        // Gym Info dialog fires this on Save — refresh name + logo live.
        AppEvents.subscribe(AppEvents.Type.GYM_INFO_UPDATED, this::renderBrand);

        for (NavEntry entry : NAV_ITEMS) {
            navContainer.getChildren().add(buildNavItem(entry));
        }
        // "Home" is selected by default, matching the screenshot.
        if (!navContainer.getChildren().isEmpty()) {
            ((SidebarNavItem) navContainer.getChildren().get(0)).setSelected(true);
        }
    }

    public void setGymNameLabel(String gymName){
        gymNameLabel.setText(gymName);
    }

    /** Repaints the brand block from the live {@link Settings} singleton:
     *  the gym name, and the circular logo (or the dumbbell fallback). */
    private void renderBrand() {
        gymNameLabel.setText(Settings.getGymName());

        Image logo = loadLogo(Settings.getLogoPath());
        boolean hasLogo = logo != null;
        brandLogoImage.setImage(logo);
        brandLogoImage.setVisible(hasLogo);
        brandLogoImage.setManaged(hasLogo);
        brandPlaceholderIcon.setVisible(!hasLogo);
        brandPlaceholderIcon.setManaged(!hasLogo);
    }

    /** Loads the logo file straight from disk (bypassing JavaFX's URL image
     *  cache, so re-picking a logo under the same stable filename still
     *  refreshes). Returns null for a missing/blank/corrupt file. */
    private Image loadLogo(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        File file = new File(path);
        if (!file.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(file)) {
            Image image = new Image(in, 68, 68, true, true);
            return image.isError() ? null : image;
        } catch (IOException e) {
            return null;
        }
    }
    /** Registers the handler invoked when the user selects a nav item that has a real page. */
    public void setOnNavigate(Consumer<Page> handler) {
        this.onNavigate = handler != null ? handler : page -> { };
    }

    private SidebarNavItem buildNavItem(NavEntry entry) {
        SidebarNavItem navItem = new SidebarNavItem(entry.icon, entry.label);
        navItem.setToggleGroup(navGroup);
        if (entry.page != null) {
            navItem.setOnAction(e -> onNavigate.accept(entry.page));
        }
        return navItem;
    }

    private static final class NavEntry {
        final String icon;
        final String label;
        final Page page;

        NavEntry(String icon, String label, Page page) {
            this.icon = icon;
            this.label = label;
            this.page = page;
        }
    }
}
