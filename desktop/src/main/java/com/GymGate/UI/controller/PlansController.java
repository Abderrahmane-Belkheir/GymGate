package com.GymGate.UI.controller;

import com.GymGate.UI.component.ConfirmDialogHelper;
import com.GymGate.UI.component.DialogEffects;
import com.GymGate.UI.component.PlanCard;
import com.GymGate.UI.component.SeancePricesPanel;
import com.GymGate.bussines.db.dao.MemberDao;
import com.GymGate.bussines.db.dao.PlanDao;
import com.GymGate.bussines.db.entities.Plan;
import com.GymGate.bussines.models.Sexe;
import com.GymGate.bussines.services.AppEvents;
import com.GymGate.bussines.services.I18nService;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Controller for the Membership Plans page (PlansView.fxml).
 * Loads existing plans via PlanDao and renders one PlanCard per plan —
 * mirrors HomeController's buildStatsGrid()/buildNotifications() pattern.
 */
public class PlansController {

    @FXML private FlowPane plansContainer;
    @FXML private VBox seanceContainer;
    @FXML private Button newPlanButton;
    @FXML private ToggleGroup genderToggleGroup;
    @FXML private ToggleButton genderMaleButton;
    @FXML private ToggleButton genderFemaleButton;

    private final MemberDao memberDao=MemberDao.getInstance();
    private final PlanDao planDao = PlanDao.getInstance();
    private final SeancePricesPanel seancePanel = new SeancePricesPanel();

    @FXML
    public void initialize() {
        seanceContainer.getChildren().setAll(seancePanel);
        loadPlans();
        newPlanButton.setOnAction(e -> openNewPlanDialog());

        preventEmptySelection(genderToggleGroup);
        for (ToggleButton option : List.of(genderMaleButton, genderFemaleButton)) {
            option.setOnAction(e -> loadPlans());
        }

        // A plan or séance price edited on the owner's phone lands here via the
        // sync pull; NavigationManager keeps this controller alive, so a plain
        // reload is enough whether or not the screen is currently on view.
        AppEvents.subscribe(AppEvents.Type.PLAN_UPDATED, this::refresh);
    }

    private static void preventEmptySelection(ToggleGroup group) {
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });
    }

    private Sexe currentSexe() {
        return genderMaleButton.isSelected() ? Sexe.MALE : Sexe.FEMALE;
    }

    private void loadPlans() {
        seancePanel.showFor(currentSexe());
        plansContainer.getChildren().clear();
        List<Plan> plans = planDao.findAll(currentSexe());
        for (Plan plan : plans) {
            PlanCard card = new PlanCard(plan, memberDao.countByPlan(plan.getId()));
            card.setOnEdit(this::openEditPlanDialog);
            card.setOnDelete(this::confirmAndDeletePlan);
            plansContainer.getChildren().add(card);
        }
    }

    /** Call after inserting/editing/deleting a plan elsewhere to refresh the grid. */
    public void refresh() {
        loadPlans();
    }

    private void openNewPlanDialog() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/NewPlanDialog.fxml"));
            loader.setResources(I18nService.getBundle());
            Parent dialogRoot = loader.load();
            NewPlanController controller = loader.getController();

            Stage dialogStage = new Stage(StageStyle.TRANSPARENT);
            dialogStage.initOwner(plansContainer.getScene().getWindow());
            dialogStage.initModality(Modality.APPLICATION_MODAL);

            Scene dialogScene = new Scene(DialogEffects.wrapForTransparentScene(dialogRoot));
            dialogScene.setFill(Color.TRANSPARENT);
            dialogScene.getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());
            dialogStage.setScene(dialogScene);
            DialogEffects.preventShowFlash(dialogStage);

            controller.setDialogStage(dialogStage);

            Parent plansRoot = plansContainer.getScene().getRoot();
            DialogEffects.applyBlurAndDim(plansRoot);
            dialogStage.setOnHidden(e -> DialogEffects.clear(plansRoot));

            dialogStage.showAndWait();

            Optional<Plan> result = controller.getResult();
            result.ifPresent(plan -> {
                plan.setSexe(currentSexe());
                planDao.insert(plan);
                refresh();
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openEditPlanDialog(Plan plan) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/NewPlanDialog.fxml"));
            loader.setResources(I18nService.getBundle());
            Parent dialogRoot = loader.load();
            NewPlanController controller = loader.getController();

            controller.loadForEdit(plan); // switches to edit mode before showing

            Stage dialogStage = new Stage(StageStyle.TRANSPARENT);
            dialogStage.initOwner(plansContainer.getScene().getWindow());
            dialogStage.initModality(Modality.APPLICATION_MODAL);

            Scene dialogScene = new Scene(DialogEffects.wrapForTransparentScene(dialogRoot));
            dialogScene.setFill(Color.TRANSPARENT);
            dialogScene.getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());
            dialogStage.setScene(dialogScene);
            DialogEffects.preventShowFlash(dialogStage);

            controller.setDialogStage(dialogStage);

            Parent plansRoot = plansContainer.getScene().getRoot();
            DialogEffects.applyBlurAndDim(plansRoot);
            dialogStage.setOnHidden(e -> DialogEffects.clear(plansRoot));

            dialogStage.showAndWait();

            controller.getResult().ifPresent(updatedPlan -> {
                planDao.update(updatedPlan);
                refresh();
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void confirmAndDeletePlan(Plan plan) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/DeleteConfirmDialog.fxml"));
            loader.setResources(I18nService.getBundle());
            Parent dialogRoot = loader.load();
            DeleteConfirmController controller = loader.getController();

            controller.setMessage(I18nService.get("Delete_Plan?"),
                    I18nService.get("This_will_permanently_delete") + plan.getName() + I18nService.get("This_cannot_be_undone."));

            Stage dialogStage = new Stage(StageStyle.TRANSPARENT);
            dialogStage.initOwner(plansContainer.getScene().getWindow());
            dialogStage.initModality(Modality.APPLICATION_MODAL);

            Scene dialogScene = new Scene(DialogEffects.wrapForTransparentScene(dialogRoot));
            dialogScene.setFill(Color.TRANSPARENT);
            dialogScene.getStylesheets().add(
                    getClass().getResource("/css/styles.css").toExternalForm());
            dialogStage.setScene(dialogScene);
            DialogEffects.preventShowFlash(dialogStage);

            controller.setDialogStage(dialogStage);

            Parent plansRoot = plansContainer.getScene().getRoot();
            DialogEffects.applyBlurAndDim(plansRoot);
            dialogStage.setOnHidden(e -> DialogEffects.clear(plansRoot));

            dialogStage.showAndWait();

            if (controller.isConfirmed()) {
                deletePlanIfUnused(plan);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Deletes the plan only when no member is currently assigned to it; otherwise
     * shows a blocking message and leaves it in place. A plan that no member uses
     * but that still has historical payments against it also can't be removed
     * (the {@code payments.plan_id} foreign key) — that surfaces as a message too
     * instead of an uncaught exception.
     */
    private void deletePlanIfUnused(Plan plan) {
        Window window = plansContainer.getScene().getWindow();
        Parent root = plansContainer.getScene().getRoot();

        int assigned = memberDao.countByPlan(plan.getId());
        if (assigned > 0) {
            String message = I18nService.get("This_plan_is_assigned_to") + " " + assigned + " "
                    + I18nService.get(assigned == 1 ? "Member" : "Members") + ". "
                    + I18nService.get("Reassign_or_cancel_these_memberships_before_deleting_the_plan");
            ConfirmDialogHelper.alert(window, root, I18nService.get("Cannot_delete_plan"), message);
            return;
        }

        try {
            planDao.delete(plan.getId());
        } catch (RuntimeException ex) {
            ConfirmDialogHelper.alert(window, root, I18nService.get("Cannot_delete_plan"),
                    I18nService.get("This_plan_has_records_and_cannot_be_deleted"));
            return;
        }
        refresh();
    }
}