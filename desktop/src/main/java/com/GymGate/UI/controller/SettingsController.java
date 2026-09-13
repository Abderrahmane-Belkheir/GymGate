package com.GymGate.UI.controller;


import com.GymGate.UI.component.DialogEffects;
import com.GymGate.bussines.models.Settings;
import com.GymGate.bussines.services.I18nService;
import com.GymGate.bussines.services.SettingsLoader;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.fxml.FXML;

import javafx.scene.control.ToggleButton;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;
import javafx.scene.Parent;
import javafx.scene.Scene;

import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javafx.fxml.FXMLLoader;

import java.io.IOException;

public class SettingsController {

    @FXML
    private StackPane accountCard;

    @FXML
    private StackPane languageCard;

    @FXML
    private ToggleButton whatsappToggle;

    private SidebarController sidebarController;

    @FXML
    public void initialize() {
        installHoverAnimation(accountCard);
        installHoverAnimation(languageCard);
        registerHandlers();

        whatsappToggle.setSelected(Settings.isWhatsappEnabled());
        updateWhatsappToggleText();
        whatsappToggle.selectedProperty().addListener((o, was, on) -> {
            Settings.setWhatsappEnabled(on);
            SettingsLoader.editWhatsapp(on);
            updateWhatsappToggleText();
        });
    }

    private void updateWhatsappToggleText() {
        whatsappToggle.setText(I18nService.get(whatsappToggle.isSelected() ? "On" : "Off"));
    }

    private void registerHandlers() {
        accountCard.setOnMouseClicked(e -> openAccount());
        languageCard.setOnMouseClicked(e -> openLanguage());
    }

    public void setSidebarController(SidebarController sidebarController){
        this.sidebarController=sidebarController;
    }
    /**
     * Opens the Gym Info card (GymInfoDialog.fxml) — lets the gym owner edit
     * the display name and logo shown in the sidebar/header. Same
     * Stage/Scene/blur wiring as openLanguage() below.
     */
    private void openAccount() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/GymInfoDialog.fxml"));
            loader.setResources(I18nService.getBundle());
            Parent dialogRoot = loader.load();
            GymInfoDialogController controller = loader.getController();
            Stage dialogStage = new Stage(StageStyle.TRANSPARENT);
            dialogStage.initOwner(accountCard.getScene().getWindow());
            dialogStage.initModality(Modality.APPLICATION_MODAL);

            Scene dialogScene = new Scene(DialogEffects.wrapForTransparentScene(dialogRoot));
            dialogScene.setFill(Color.TRANSPARENT);
            dialogScene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            dialogStage.setScene(dialogScene);
            DialogEffects.preventShowFlash(dialogStage);

            controller.setDialogStage(dialogStage);

            Parent settingsRoot = accountCard.getScene().getRoot();
            DialogEffects.applyBlurAndDim(settingsRoot);
            dialogStage.setOnHidden(e -> DialogEffects.clear(settingsRoot));

            dialogStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Opens the language picker card (LanguageDialog.fxml). Choosing en/fr/ar
     * in there asks for confirmation, and only on Confirm does
     * LanguageDialogController run SettingsLoader.editLanguage(code) +
     * RestartAppService.restart() — nothing here runs blindly anymore.
     */
    private void openLanguage() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/LanguageDialog.fxml"));
            loader.setResources(I18nService.getBundle());
            Parent dialogRoot = loader.load();
            LanguageDialogController controller = loader.getController();

            Stage dialogStage = new Stage(StageStyle.TRANSPARENT);
            dialogStage.initOwner(languageCard.getScene().getWindow());
            dialogStage.initModality(Modality.APPLICATION_MODAL);

            Scene dialogScene = new Scene(DialogEffects.wrapForTransparentScene(dialogRoot));
            dialogScene.setFill(Color.TRANSPARENT);
            dialogScene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());
            dialogStage.setScene(dialogScene);
            DialogEffects.preventShowFlash(dialogStage);

            controller.setDialogStage(dialogStage);

            Parent settingsRoot = languageCard.getScene().getRoot();
            DialogEffects.applyBlurAndDim(settingsRoot);
            dialogStage.setOnHidden(e -> DialogEffects.clear(settingsRoot));

            dialogStage.showAndWait();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void installHoverAnimation(StackPane card) {

        ScaleTransition scaleIn = new ScaleTransition(Duration.millis(180), card);
        scaleIn.setToX(1.02);
        scaleIn.setToY(1.02);

        ScaleTransition scaleOut = new ScaleTransition(Duration.millis(180), card);
        scaleOut.setToX(1);
        scaleOut.setToY(1);

        TranslateTransition up = new TranslateTransition(Duration.millis(180), card);
        up.setToY(-4);

        TranslateTransition down = new TranslateTransition(Duration.millis(180), card);
        down.setToY(0);

        card.setOnMouseEntered(e -> {
            scaleIn.playFromStart();
            up.playFromStart();
        });

        card.setOnMouseExited(e -> {
            scaleOut.playFromStart();
            down.playFromStart();
        });
    }
}