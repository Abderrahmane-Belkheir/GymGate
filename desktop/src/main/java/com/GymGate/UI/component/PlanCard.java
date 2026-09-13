package com.GymGate.UI.component;

import com.GymGate.bussines.db.entities.Plan;
import com.GymGate.bussines.services.I18nService;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * A single membership plan card (name, duration, price, days/month,
 * cardio included) as shown on the Membership Plans page.
 *
 * Purely a display component — it has no DAO/DB knowledge. It's built
 * programmatically (not FXML) because PlansController creates one per
 * row returned from PlanDao, same reasoning as StatCard/NotificationRow.
 */
import javafx.scene.control.Button;
import org.kordamp.ikonli.javafx.FontIcon;
import java.util.function.Consumer;

public class PlanCard extends VBox {

    private static final NumberFormat PRICE_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private Consumer<Plan> onEdit;
    private Consumer<Plan> onDelete;

    public PlanCard(Plan plan, int enrolledMembers) {
        getStyleClass().add("plan-card");
        setSpacing(14);
        setPrefWidth(280);

        Label nameLabel = new Label(plan.getName());
        nameLabel.getStyleClass().add("plan-name");

        Label durationLabel = new Label(formatDuration(plan.getDurationMonths()));
        durationLabel.getStyleClass().add("plan-duration");

        VBox headerBox = new VBox(2, nameLabel, durationLabel);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button editButton = buildIconButton("fas-pen", "plan-edit-button");
        editButton.setOnAction(e -> { if (onEdit != null) onEdit.accept(plan); });

        Button deleteButton = buildIconButton("fas-trash", "plan-delete-button");
        deleteButton.setOnAction(e -> { if (onDelete != null) onDelete.accept(plan); });

        HBox actionsRow = new HBox(6, editButton, deleteButton);
        actionsRow.setAlignment(Pos.CENTER_RIGHT);

        HBox headerRow = new HBox(headerBox, headerSpacer, actionsRow);
        headerRow.setAlignment(Pos.TOP_LEFT);

        getChildren().addAll(
                headerRow,
                buildDivider(),
                buildPriceBlock(plan),
                buildDivider(),
                buildInfoRow(I18nService.get("Enrolled"), String.valueOf(enrolledMembers)),
                buildInfoRow(I18nService.get("Days_per_month"), formatDays(plan.getDaysPerMonth())),
                buildBadgeRow(I18nService.get("Cardio_Included"), plan.isCardioIncluded())
        );
    }

    private Button buildIconButton(String iconLiteral, String styleClass) {
        Button button = new Button();
        button.getStyleClass().add(styleClass);
        button.setFocusTraversable(false);
        FontIcon icon = new FontIcon(iconLiteral);
        icon.getStyleClass().add("plan-action-icon");
        button.setGraphic(icon);
        return button;
    }

    private Separator buildDivider() {
        Separator separator = new Separator();
        separator.getStyleClass().add("plan-divider");
        return separator;
    }

    private VBox buildPriceBlock(Plan plan) {
        Label captionLabel = new Label(I18nService.get("Price"));
        captionLabel.getStyleClass().add("plan-price-caption");

        Label valueLabel = new Label(PRICE_FORMAT.format(plan.getPrice()));
        valueLabel.getStyleClass().add("plan-price-value");

        Label currencyLabel = new Label("DZD");
        currencyLabel.getStyleClass().add("plan-price-currency");

        HBox valueRow = new HBox(6, valueLabel, currencyLabel);
        valueRow.setAlignment(Pos.BASELINE_LEFT);

        VBox block = new VBox(4, captionLabel, valueRow);
        block.getStyleClass().add("plan-price-block");
        return block;
    }

    private HBox buildInfoRow(String label, String value) {
        Label captionLabel = new Label(label);
        captionLabel.getStyleClass().add("plan-row-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("plan-row-value");

        HBox row = new HBox(captionLabel, spacer, valueLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private HBox buildBadgeRow(String label, boolean value) {
        Label captionLabel = new Label(label);
        captionLabel.getStyleClass().add("plan-row-label");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Badge badge = new Badge(value ? "Yes" : "No", value ? "badge-yes" : "badge-no");

        HBox row = new HBox(captionLabel, spacer, badge);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private String formatDuration(int months) {
        return months + (months == 1 ? I18nService.get("month") : I18nService.get("months"));
    }

    private String formatDays(Integer daysPerMonth) {
        return daysPerMonth == null ? I18nService.get("Unlimited") : String.valueOf(daysPerMonth);
    }

    public void setOnEdit(Consumer<Plan> handler) {
        this.onEdit = handler;
    }

    public void setOnDelete(Consumer<Plan> handler) {
        this.onDelete = handler;
    }
}