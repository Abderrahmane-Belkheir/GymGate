
package com.GymGate.UI.component;

import com.GymGate.bussines.db.dao.PlanDao;
import com.GymGate.bussines.db.entities.Plan;
import com.GymGate.bussines.models.Sexe;
import com.GymGate.bussines.services.I18nService;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;

/**
 * Reusable "pick a membership plan" list — selectable PlanSelectionTile rows
 * sharing one ToggleGroup, inside a scroll area that caps its height so a long
 * plan list scrolls instead of stretching the dialog off-screen. Used by both
 * the registration flow (RegisterMemberController) and the renew flow
 * (RenewPlanController), so the plan-picking logic lives in exactly one place.
 */
public class PlanSelectionPanel extends ScrollPane {

    /** Height at which the list stops growing and starts scrolling (~4 tiles). */
    private static final double MAX_VISIBLE_HEIGHT = 340;

    private final PlanDao planDao = PlanDao.getInstance();
    private final ToggleGroup toggleGroup = new ToggleGroup();
    private final VBox list = new VBox(10);
    private Plan selectedPlan;
    private Runnable onSelectionChanged;

    public PlanSelectionPanel() {
        getStyleClass().add("plan-selection-panel");
        list.getStyleClass().add("plan-selection-list");

        setContent(list);
        setFitToWidth(true);
        setHbarPolicy(ScrollBarPolicy.NEVER);
        setVbarPolicy(ScrollBarPolicy.AS_NEEDED);
        setPannable(true);
        setMinHeight(0);
        setMaxHeight(MAX_VISIBLE_HEIGHT);

        toggleGroup.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            selectedPlan = (newToggle instanceof PlanSelectionTile tile) ? tile.getPlan() : null;
            if (onSelectionChanged != null) {
                onSelectionChanged.run();
            }
        });
    }

    /** (Re)loads the plan list from the database, restricted to the given gender, and clears any previous selection. */
    public void loadPlans(boolean includeCustom, Sexe sexe) {
        list.getChildren().clear();
        selectedPlan = null;

        List<Plan> plans = planDao.findAll(sexe);
        for (Plan plan : plans) {
            PlanSelectionTile tile = new PlanSelectionTile(plan);
            tile.setToggleGroup(toggleGroup);
            list.getChildren().add(tile);
        }

        if (includeCustom) {
            PlanSelectionTile custom = new PlanSelectionTile(new Plan(I18nService.get("Custom")));
            custom.setToggleGroup(toggleGroup);
            list.getChildren().add(custom);
        }

        setVvalue(0);
    }


    public void preselect(int planId) {
        for (var node : list.getChildren()) {
            if (node instanceof PlanSelectionTile tile && tile.getPlan().getId() == planId) {
                tile.setSelected(true);
                break;
            }
        }
    }

    public Optional<Plan> getSelectedPlan() {
        return Optional.ofNullable(selectedPlan);
    }

    public void setOnSelectionChanged(Runnable handler) {
        this.onSelectionChanged = handler;
    }
}
