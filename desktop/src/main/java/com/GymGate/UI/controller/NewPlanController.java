package com.GymGate.UI.controller;

import com.GymGate.bussines.db.entities.Plan;
import com.GymGate.bussines.services.I18nService;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Optional;

/**
 * Controller for the "New Plan" dialog (NewPlanDialog.fxml).
 *
 * Purely a form: validates input and hands back a fully-built Plan on
 * success. It never calls PlanDao — the caller (PlansController) owns
 * persistence, mirroring how RegisterMemberController hands a
 * RegistrationData back to HomeController rather than inserting itself.
 */
public class NewPlanController {

    @FXML private Label dialogTitleLabel;
    @FXML private Label dialogSubtitleLabel;
    @FXML private FontIcon createButtonIcon;
    @FXML private Label createButtonLabel;

    @FXML private TextField nameField;
    @FXML private TextField durationField;
    @FXML private TextField daysPerMonthField;
    @FXML private TextField priceField;
    @FXML private CheckBox cardioCheckBox;

    @FXML private Button createButton;
    @FXML private Button cancelButton;
    @FXML private Button closeButton;

    private Stage dialogStage;
    private Plan result;
    private Integer editingPlanId; // null = creating a new plan, non-null = editing an existing one

    @FXML
    private void initialize() {
        BooleanBinding formInvalid = Bindings.createBooleanBinding(
                () -> !isFormValid(),
                nameField.textProperty(),
                durationField.textProperty(),
                daysPerMonthField.textProperty(),
                priceField.textProperty()
        );
        createButton.disableProperty().bind(formInvalid);

        createButton.setOnAction(e -> {
            result = buildPlan();
            closeDialog();
        });
        cancelButton.setOnAction(e -> closeDialog());
        closeButton.setOnAction(e -> closeDialog());
    }

    /** Call this before showing the dialog to switch it into edit mode, pre-filled with the given plan. */
    public void loadForEdit(Plan plan) {
        editingPlanId = plan.getId();

        nameField.setText(plan.getName());
        durationField.setText(String.valueOf(plan.getDurationMonths()));
        daysPerMonthField.setText(plan.getDaysPerMonth() != null ? String.valueOf(plan.getDaysPerMonth()) : "");
        priceField.setText(formatPriceForEditing(plan.getPrice()));
        cardioCheckBox.setSelected(plan.isCardioIncluded());

        dialogTitleLabel.setText(I18nService.get("Edit_Plan"));
        dialogSubtitleLabel.setText(I18nService.get("Update_this_membership_plan's_details."));
        createButtonIcon.setIconLiteral("fas-save");
        createButtonLabel.setText(I18nService.get("Save_Changes"));
    }

    private String formatPriceForEditing(double price) {
        // Avoids "6900.0" showing up in the field — whole numbers display cleanly,
        // while still allowing decimal prices to round-trip correctly.
        return price == Math.floor(price) ? String.valueOf((long) price) : String.valueOf(price);
    }

    private boolean isFormValid() {
        return parseName() != null
                && parseDurationMonths() != null
                && isDaysPerMonthValid()
                && parsePrice() != 0;
    }

    private String parseName() {
        String name = nameField.getText();
        return (name != null && !name.isBlank()) ? name.trim() : null;
    }

    private Integer parseDurationMonths() {
        try {
            int v = Integer.parseInt(durationField.getText().trim());
            return v > 0 ? v : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isDaysPerMonthValid() {
        String text = daysPerMonthField.getText();
        if (text == null || text.isBlank()) {
            return true;
        }
        try {
            return Integer.parseInt(text.trim()) > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private Integer resolveDaysPerMonth() {
        String text = daysPerMonthField.getText();
        return (text == null || text.isBlank()) ? null : Integer.parseInt(text.trim());
    }

    private int parsePrice() {
        try {
            int v = Integer.parseInt(priceField.getText().trim());
            return v >= 0 ? v : null;
        } catch (Exception e) {
            return 0;
        }
    }

    private Plan buildPlan() {
        Plan plan = new Plan(
                parseName(),
                parseDurationMonths(),
                resolveDaysPerMonth(),
                parsePrice(),
                cardioCheckBox.isSelected(),null
        );
        if (editingPlanId != null) {
            plan.setId(editingPlanId); // carries the original id forward for update()
        }
        return plan;
    }

    private void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    /** true if this dialog was opened via loadForEdit(), false for a brand-new plan. */
    public boolean isEditMode() {
        return editingPlanId != null;
    }

    public Optional<Plan> getResult() {
        return Optional.ofNullable(result);
    }
}