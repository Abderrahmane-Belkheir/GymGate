package com.GymGate.UI.component;

import com.GymGate.bussines.db.dao.SeanceDao;
import com.GymGate.bussines.db.entities.Seance;
import com.GymGate.bussines.models.Sexe;
import com.GymGate.bussines.services.I18nService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.text.NumberFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * "Register a single session" picker, opened from the Home action bar. Shows the
 * four {@code seance} prices in two columns — Male (with-cardio above standard)
 * and Female — as selectable tiles, with a Confirm button.
 *
 * <p>{@link #show} returns the picked {@link Seance} on Confirm (or {@code null}
 * if cancelled); the caller records the payment and refreshes the stats.
 */
public final class SeanceSaleDialog {

    private static final NumberFormat PRICE_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private Seance result;

    private SeanceSaleDialog() {
    }

    /** @return the picked séance, or {@code null} if the dialog was cancelled. */
    public static Seance show(Window owner, Parent ownerRoot) {
        return new SeanceSaleDialog().run(owner, ownerRoot);
    }

    private Seance run(Window owner, Parent ownerRoot) {
        List<Seance> all = SeanceDao.getInstance().findAll();
        ToggleGroup group = new ToggleGroup();

        Label title = new Label(I18nService.get("Single_Session"));
        title.getStyleClass().add("seance-sale-title");

        HBox columns = new HBox(16, column(Sexe.MALE, all, group), column(Sexe.FEMALE, all, group));
        columns.setAlignment(Pos.CENTER);

        Button cancel = new Button(I18nService.get("Cancel"));
        cancel.getStyleClass().add("seance-sale-cancel");
        cancel.setMaxWidth(Double.MAX_VALUE);
        Button confirm = new Button(I18nService.get("Confirm"));
        confirm.getStyleClass().add("seance-sale-confirm");
        confirm.setDefaultButton(true);
        confirm.setMaxWidth(Double.MAX_VALUE);
        confirm.setDisable(true);

        group.selectedToggleProperty().addListener((obs, oldT, newT) -> {
            if (newT == null && oldT != null) {   // can't unpick, only switch
                oldT.setSelected(true);
                return;
            }
            confirm.setDisable(newT == null);
        });
        HBox.setHgrow(cancel, Priority.ALWAYS);
        HBox.setHgrow(confirm, Priority.ALWAYS);
        HBox buttons = new HBox(12, cancel, confirm);
        buttons.setAlignment(Pos.CENTER);

        VBox card = new VBox(18, title, columns, buttons);
        card.getStyleClass().add("seance-sale-card");
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(28, 30, 24, 30));

        StackPane wrapper = new StackPane(card);
        wrapper.setStyle("-fx-background-color: transparent;");
        wrapper.setPadding(new Insets(36));

        Scene scene = new Scene(wrapper);
        scene.setFill(Color.TRANSPARENT);
        scene.getStylesheets().add(getClass().getResource("/css/styles.css").toExternalForm());

        Stage stage = new Stage(StageStyle.TRANSPARENT);
        if (owner != null) {
            stage.initOwner(owner);
        }
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setScene(scene);
        DialogEffects.preventShowFlash(stage);

        cancel.setOnAction(e -> stage.close());
        confirm.setOnAction(e -> {
            if (group.getSelectedToggle() != null
                    && group.getSelectedToggle().getUserData() instanceof Seance s) {
                result = s;
            }
            stage.close();
        });

        if (ownerRoot != null) {
            DialogEffects.applyBlurAndDim(ownerRoot);
            stage.setOnHidden(e -> DialogEffects.clear(ownerRoot));
        }
        stage.showAndWait();
        return result;
    }

    private VBox column(Sexe sexe, List<Seance> all, ToggleGroup group) {
        boolean male = sexe == Sexe.MALE;
        Label head = new Label(I18nService.get(male ? "Male" : "Female"));
        head.getStyleClass().add(male ? "seance-sale-head-male" : "seance-sale-head-female");

        VBox col = new VBox(10, head);
        col.getStyleClass().add("seance-sale-column");
        col.setAlignment(Pos.TOP_CENTER);
        col.setFillWidth(true);

        all.stream()
                .filter(s -> s.getSexe() == sexe)
                .sorted(Comparator.comparing(Seance::isCardio).reversed())   // cardio above standard
                .forEach(s -> col.getChildren().add(tile(s, group)));
        return col;
    }

    private ToggleButton tile(Seance s, ToggleGroup group) {
        Label kind = new Label(I18nService.get(s.isCardio() ? "With_cardio" : "Standard"));
        kind.getStyleClass().add("seance-sale-tile-kind");
        Label price = new Label(PRICE_FORMAT.format((long) s.getPrice()) + " DZD");
        price.getStyleClass().add("seance-sale-tile-price");
        VBox content = new VBox(3, kind, price);
        content.setAlignment(Pos.CENTER);

        ToggleButton tile = new ToggleButton();
        tile.setGraphic(content);
        tile.setToggleGroup(group);
        tile.getStyleClass().add("seance-sale-tile");
        tile.setFocusTraversable(false);
        tile.setMaxWidth(Double.MAX_VALUE);
        tile.setUserData(s);
        return tile;
    }
}
