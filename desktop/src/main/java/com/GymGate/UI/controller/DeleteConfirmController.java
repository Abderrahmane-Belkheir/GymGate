package com.GymGate.UI.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class DeleteConfirmController {

    @FXML private Label titleLabel;
    @FXML private Label messageLabel;
    @FXML private Button deleteButton;
    @FXML private Button cancelButton;

    private Stage dialogStage;
    private boolean confirmed = false;

    @FXML
    private void initialize() {
        deleteButton.setOnAction(e -> {
            confirmed = true;
            closeDialog();
        });
        cancelButton.setOnAction(e -> closeDialog());
    }

    /** Customize the wording for what's being deleted, e.g. the plan's name. */
    public void setMessage(String title, String message) {
        titleLabel.setText(title);
        messageLabel.setText(message);

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