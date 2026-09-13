package com.GymGate.UI.controller;

import com.GymGate.bussines.services.I18nService;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;


public class ConfirmDialogController {

    @FXML private FontIcon dialogIcon;
    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private Button cancelButton;
    @FXML private Button confirmButton;

    private Stage dialogStage;
    private boolean confirmed = false;

    @FXML
    private void initialize() {
        confirmButton.setOnAction(e -> {
            confirmed = true;
            closeDialog();
        });
        cancelButton.setOnAction(e -> closeDialog());
    }

    /** Sets the wording shown for this particular confirmation. */
    public void setContent(String title, String message) {
        titleLabel.setText(title);
        messageLabel.setText(message);
    }

    /** Turns the dialog into a single-button acknowledgement (no choice to make):
     *  the Cancel button is removed, the primary button becomes "OK", and the
     *  icon switches from the question mark to a warning triangle. Used for
     *  "this action can't be done" messages. */
    public void configureAsAlert() {
        cancelButton.setVisible(false);
        cancelButton.setManaged(false);
        confirmButton.setText(I18nService.get("OK"));
        if (dialogIcon != null) {
            dialogIcon.setIconLiteral("fas-exclamation-triangle");
        }
    }

    private void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public boolean isConfirmed() {
        return confirmed;
    }
}