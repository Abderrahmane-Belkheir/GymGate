package com.GymGate.UI.controller;



import com.GymGate.UI.component.ConfirmDialogHelper;
import com.GymGate.bussines.services.I18nService;
import com.GymGate.bussines.services.RestartAppService;
import com.GymGate.bussines.services.SettingsLoader;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.opencv.imgcodecs.Imgcodecs;

/**
 * Controller for LanguageDialog.fxml — the language-picker card opened
 * from SettingsController.openLanguage().
 *
 * Clicking a language tile does NOT apply it immediately: it first asks
 * for confirmation via {@link ConfirmDialogHelper}, and only on Confirm
 * runs the same two calls SettingsController.openLanguage() used to run
 * directly — {@code SettingsLoader.editLanguage(code)} then
 * {@code RestartAppService.restart()} — parameterized by whichever
 * language was actually clicked (en / fr / ar).
 */
public class LanguageDialogController {

    @FXML private HBox englishOption;
    @FXML private HBox frenchOption;
    @FXML private HBox arabicOption;
    @FXML private Button closeButton;

    private Stage dialogStage;

    @FXML
    public void initialize() {
        englishOption.setOnMouseClicked(e -> confirmAndApply("en"));
        frenchOption.setOnMouseClicked(e -> confirmAndApply("fr"));
        arabicOption.setOnMouseClicked(e -> confirmAndApply("ar"));
        closeButton.setOnAction(e -> closeDialog());
    }

    private void confirmAndApply(String languageCode) {
        String languageName = I18nService.get("language_name_" + languageCode);

        boolean confirmed = ConfirmDialogHelper.ask(
                dialogStage,
                (Parent) englishOption.getScene().getRoot(),
                I18nService.get("confirm_language_title"),
                I18nService.get("confirm_language_message_prefix")
                        + " " + languageName
                        + " " + I18nService.get("confirm_language_message_suffix")
        );

        if (confirmed) {
            openLanguage(languageCode);
        }
    }

    /** Same two calls SettingsController.openLanguage() used to run, now parameterized. */
    private void openLanguage(String languageCode) {
        SettingsLoader.editLanguage(languageCode);

        RestartAppService.restart();

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