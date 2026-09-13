package com.GymGate.UI.controller;


import com.GymGate.UI.component.PlanSelectionPanel;
import com.GymGate.bussines.db.entities.Plan;
import com.GymGate.bussines.models.Sexe;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.Optional;

public class  RenewPlanController {

    @FXML private Label dialogSubtitleLabel;
    @FXML private VBox planListContainer;
    @FXML private Button confirmRenewButton;
    @FXML private Button cancelButton;
    @FXML private Button closeButton;

    private final PlanSelectionPanel planSelectionPanel = new PlanSelectionPanel();

    private Stage dialogStage;
    private Plan resultPlan;

    @FXML
    private void initialize() {
        planListContainer.getChildren().add(planSelectionPanel);

        planSelectionPanel.setOnSelectionChanged(() ->
                confirmRenewButton.setDisable(planSelectionPanel.getSelectedPlan().isEmpty()));
        confirmRenewButton.setDisable(true);

        confirmRenewButton.setOnAction(e -> {
            resultPlan = planSelectionPanel.getSelectedPlan().orElse(null);
            closeDialog();
        });
        cancelButton.setOnAction(e -> closeDialog());
        closeButton.setOnAction(e -> closeDialog());
    }

    /** Call before showing — populates the list (restricted to the member's gender) and highlights the member's current plan, if any. */
    public void prepareForShow(String memberName, Integer currentPlanId, Sexe sexe) {
        resultPlan = null;
        dialogSubtitleLabel.setText("Select a new plan for " + memberName + ".");
        planSelectionPanel.loadPlans(false, sexe);
        if (currentPlanId != null) {
            planSelectionPanel.preselect(currentPlanId);
        }
        confirmRenewButton.setDisable(planSelectionPanel.getSelectedPlan().isEmpty());
    }

    private void closeDialog() {
        if (dialogStage != null) {
            dialogStage.hide();
        }
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    public Optional<Plan> getResult() {
        return Optional.ofNullable(resultPlan);
    }
}