package com.GymGate.UI.component;


import com.GymGate.bussines.db.entities.Plan;
import com.GymGate.bussines.services.I18nService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.util.Locale;

public class PlanSelectionTile extends ToggleButton {

    private static final NumberFormat PRICE_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private final Plan plan;




    public PlanSelectionTile(Plan plan) {
        this.plan = plan;
        getStyleClass().add("plan-selection-tile");
        setMaxWidth(Double.MAX_VALUE);
        setFocusTraversable(false);
        setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(plan.getName());
        nameLabel.getStyleClass().add("plan-tile-name");
        Label durationLabel=new Label();
        if(!plan.getName().equalsIgnoreCase(I18nService.get("Custom"))){
            durationLabel = new Label(formatDuration(plan.getDurationMonths()));
            durationLabel.getStyleClass().add("plan-tile-duration");
        }


        VBox headerBox = new VBox(2, nameLabel, durationLabel);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label priceLabel=new Label();
        if(!plan.getName().equalsIgnoreCase(I18nService.get("Custom"))){
             priceLabel = new Label(PRICE_FORMAT.format(plan.getPrice()) + " DZD");
            priceLabel.getStyleClass().add("plan-tile-price");
        }

        HBox row = new HBox(10, headerBox, spacer, priceLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14, 16, 14, 16));

        setGraphic(row);
        setText(null);
    }

    private String formatDuration(int months) {
        String base = months + (months == 1 ? I18nService.get("month") : I18nService.get("months"));
        return plan.isCardioIncluded() ? base + " · "+I18nService.get("Cardio_Included"): base;
    }

    public Plan getPlan() {
        return plan;
    }
}