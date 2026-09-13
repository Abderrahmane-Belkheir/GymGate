package com.GymGate.UI.controller;

import com.GymGate.UI.navigation.NavigationManager;
import com.GymGate.bussines.models.Settings;
import com.GymGate.bussines.services.AppEvents;
import com.GymGate.bussines.services.RestartAppService;
import com.GymGate.bussines.services.SettingsLoader;
import com.GymGate.bussines.util.AppPaths;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.NavigableMap;
import java.util.function.Consumer;

/**
 * Controller for GymInfoDialog.fxml — opened from SettingsController.
 *
 * Follows the same deferred-save pattern as LanguageDialogController:
 * nothing is written to disk or to the live {@link Settings} singleton
 * until Save is clicked. On Save the new name/logo are persisted, pushed
 * into the live Settings singleton, and an {@link AppEvents.Type#GYM_INFO_UPDATED}
 * event is fired so the sidebar refreshes its brand in real time — no
 * app restart needed.
 */
public class GymInfoDialogController {

    @FXML private Button closeButton;
    @FXML private StackPane logoFrame;
    @FXML private FontIcon logoPlaceholderIcon;
    @FXML private ImageView logoImageView;
    @FXML private Button changeLogoButton;
    @FXML private TextField gymNameField;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    private Stage dialogStage;

    /** Logo the user picked in this session but hasn't saved yet — only
     *  copied into the app's data dir and persisted on Save, so Cancel
     *  leaves the previously-saved logo untouched. */
    private File pendingLogoFile;

    @FXML
    public void initialize() {
        gymNameField.setText(Settings.getGymName());
        showLogo(Settings.getLogoPath());

        changeLogoButton.setOnAction(e -> pickLogo());
        saveButton.setOnAction(e -> save());
        cancelButton.setOnAction(e -> closeDialog());
        closeButton.setOnAction(e -> closeDialog());

        saveButton.disableProperty().bind(gymNameField.textProperty().isEmpty());
    }

    private void pickLogo() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select Gym Logo");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg")
        );
        File chosen = chooser.showOpenDialog(dialogStage);
        if (chosen == null) {
            return;
        }

        pendingLogoFile = chosen;
        showLogo(chosen.toURI().toString());
    }

    /** Shows a path/URI in the preview if it resolves to a real image, otherwise
     *  falls back to the placeholder icon (e.g. path is null on first-ever run). */
    private void showLogo(String pathOrUri) {
        if (pathOrUri == null || pathOrUri.isBlank()) {
            logoImageView.setVisible(false);
            logoImageView.setManaged(false);
            logoPlaceholderIcon.setVisible(true);
            logoPlaceholderIcon.setManaged(true);
            return;
        }

        // pathOrUri is either a real URL (file:/http:/... from pickLogo) or a bare
        // filesystem path from Settings — a Windows path like "C:\..." also contains
        // ':', so test for an actual scheme, not just any colon.
        boolean isUrl = pathOrUri.matches("(?i)^[a-z][a-z0-9+.-]*:/.*");
        String uri = isUrl ? pathOrUri : new File(pathOrUri).toURI().toString();
        // Load synchronously (no background loading) so image.isError() below is meaningful.
        Image image = new Image(uri, 76, 76, true, true, false);
        if (image.isError()) {
            // Corrupt/missing file on disk — don't crash the dialog over a broken logo,
            // just fall back to the placeholder like there was never a logo set.
            logoImageView.setVisible(false);
            logoImageView.setManaged(false);
            logoPlaceholderIcon.setVisible(true);
            logoPlaceholderIcon.setManaged(true);
            return;
        }

        logoImageView.setImage(image);
        logoImageView.setVisible(true);
        logoImageView.setManaged(true);
        logoPlaceholderIcon.setVisible(false);
        logoPlaceholderIcon.setManaged(false);
    }

    private void save() {
        String newName = gymNameField.getText().trim();
        if (newName.isEmpty()) {
            return;
        }
        SettingsLoader.editGymName(newName);
        Settings.setGymName(newName);

        if (pendingLogoFile != null) {
            try {
                String storedLogoPath = copyLogoIntoDataDir(pendingLogoFile);
                SettingsLoader.editGymLogo(storedLogoPath);
                Settings.setLogoPath(storedLogoPath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        // Tell the sidebar (and anyone else showing the brand) to refresh now.
        AppEvents.publish(AppEvents.Type.GYM_INFO_UPDATED);
        closeDialog();
    }

    /** Copies the chosen file into the app's writable data dir under a stable
     *  filename (gym-logo.<ext>), so the stored path survives even if the
     *  user later moves/deletes the original file they picked it from. */
    private String copyLogoIntoDataDir(File source) throws IOException {
        String name = source.getName();
        String extension = name.contains(".")
                ? name.substring(name.lastIndexOf('.'))
                : ".png";

        File destination = AppPaths.resolve("gym-logo" + extension);
        Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
        return destination.getAbsolutePath();
    }

    private void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }
}