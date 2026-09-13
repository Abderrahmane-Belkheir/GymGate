package com.GymGate.UI.component;

import com.GymGate.UI.controller.ConfirmDialogController;
import com.GymGate.bussines.services.I18nService;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.IOException;


public final class ConfirmDialogHelper {

    private ConfirmDialogHelper() {
    }

    /**
     * Shows the confirm dialog and blocks until the user answers.
     *
     * @param ownerWindow the window the dialog belongs to (used for initOwner) — may be null
     * @param ownerRoot   the root Node to blur/dim behind the dialog — may be null to skip the effect
     * @param title       text shown as the dialog's title
     * @param message     text shown as the dialog's message
     * @return true if the user clicked Confirm, false if they clicked Cancel, closed the
     *         dialog, or the FXML failed to load
     */
    public static boolean ask(Window ownerWindow, Parent ownerRoot, String title, String message) {
        return show(ownerWindow, ownerRoot, title, message, false);
    }

    /**
     * Shows a single-button "OK" acknowledgement and blocks until dismissed —
     * for "this can't be done" messages where there is nothing to confirm.
     */
    public static void alert(Window ownerWindow, Parent ownerRoot, String title, String message) {
        show(ownerWindow, ownerRoot, title, message, true);
    }

    private static boolean show(Window ownerWindow, Parent ownerRoot, String title, String message, boolean alert) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    ConfirmDialogHelper.class.getResource("/fxml/ConfirmDialog.fxml"));
            loader.setResources(I18nService.getBundle());
            Parent dialogRoot = loader.load();
            ConfirmDialogController controller = loader.getController();
            controller.setContent(title, message);
            if (alert) {
                controller.configureAsAlert();
            }

            Stage dialogStage = new Stage(StageStyle.TRANSPARENT);
            if (ownerWindow != null) {
                dialogStage.initOwner(ownerWindow);
            }
            dialogStage.initModality(Modality.APPLICATION_MODAL);

            Scene dialogScene = new Scene(DialogEffects.wrapForTransparentScene(dialogRoot));
            dialogScene.setFill(Color.TRANSPARENT);
            dialogScene.getStylesheets().add(
                    ConfirmDialogHelper.class.getResource("/css/styles.css").toExternalForm());
            dialogStage.setScene(dialogScene);
            DialogEffects.preventShowFlash(dialogStage);

            controller.setDialogStage(dialogStage);

            if (ownerRoot != null) {
                DialogEffects.applyBlurAndDim(ownerRoot);
                dialogStage.setOnHidden(e -> DialogEffects.clear(ownerRoot));
            }

            dialogStage.showAndWait();
            return controller.isConfirmed();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}