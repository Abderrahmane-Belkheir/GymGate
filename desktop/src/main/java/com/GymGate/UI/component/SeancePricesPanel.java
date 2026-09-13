package com.GymGate.UI.component;

import com.GymGate.bussines.db.dao.SeanceDao;
import com.GymGate.bussines.db.entities.Seance;
import com.GymGate.bussines.models.Sexe;
import com.GymGate.bussines.services.I18nService;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

/**
 * The single-session ("séance") drop-in prices on the Membership Plans page —
 * two editable cards (with-cardio / standard) for the gender currently selected
 * by the page's Male/Female toggle. Each price has a small blue confirm
 * checkmark; it commits straight to {@code seance} via {@link SeanceDao} (also
 * on Enter or focus loss). No save button for the panel as a whole.
 */
public class SeancePricesPanel extends VBox {

    private final SeanceDao dao = SeanceDao.getInstance();
    private final FlowPane cards = new FlowPane(16, 16);

    private static String asText(double price) {
        return String.valueOf((long) price);
    }

    public SeancePricesPanel() {
        getStyleClass().add("seance-panel");
        setSpacing(14);

        Label title = new Label(I18nService.get("Single_Session"));
        title.getStyleClass().add("seance-title");
        Label hint = new Label(I18nService.get("Single_Session_hint"));
        hint.getStyleClass().add("seance-hint");

        getChildren().setAll(new VBox(2, title, hint), cards);
    }

    /** Rebuilds the cards for one gender. Call it whenever the toggle changes. */
    public void showFor(Sexe sexe) {
        cards.getChildren().clear();
        for (Seance s : dao.findAll()) {
            if (s.getSexe() == sexe) {
                cards.getChildren().add(priceCard(s));
            }
        }
    }

    private VBox priceCard(Seance s) {
        Label kind = new Label(I18nService.get(s.isCardio() ? "With_cardio" : "Standard"));
        kind.getStyleClass().add(s.isCardio() ? "seance-cardio-on" : "seance-cardio-off");
        HBox header = new HBox(kind);
        header.setAlignment(Pos.CENTER_LEFT);

        TextField price = new TextField(asText(s.getPrice()));
        price.getStyleClass().add("seance-price-field");
        price.setPrefColumnCount(6);
        price.setTextFormatter(new TextFormatter<>(c ->
                c.getControlNewText().matches("\\d{0,9}") ? c : null));

        Label currency = new Label("DZD");
        currency.getStyleClass().add("seance-currency");

        FontIcon checkIcon = new FontIcon("fas-check");
        checkIcon.getStyleClass().add("seance-confirm-icon");
        Button confirm = new Button();
        confirm.setGraphic(checkIcon);
        confirm.getStyleClass().add("seance-confirm-button");
        confirm.setFocusTraversable(false);

        Runnable commit = () -> {
            String text = price.getText().trim();
            if (text.isEmpty()) {
                price.setText(asText(s.getPrice()));
                return;
            }
            try {
                long value = Long.parseLong(text);
                if (value != (long) s.getPrice()) {
                    dao.updatePrice(s.getId(), value);
                    s.setPrice(value);
                }
                price.setText(String.valueOf(value));
            } catch (RuntimeException e) {
                System.err.println("Séance price not saved for row " + s.getId() + ": " + e.getMessage());
                price.setText(asText(s.getPrice()));
            }
        };
        // The checkmark is live only while there is an unsaved change.
        Runnable syncConfirm = () -> confirm.setDisable(price.getText().trim().equals(asText(s.getPrice())));
        price.textProperty().addListener((o, a, b) -> syncConfirm.run());
        syncConfirm.run();

        confirm.setOnAction(e -> commit.run());
        price.setOnAction(e -> commit.run());
        price.focusedProperty().addListener((obs, was, focused) -> {
            if (was && !focused) {
                commit.run();
            }
        });

        HBox priceRow = new HBox(6, price, currency, confirm);
        priceRow.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(10, header, priceRow);
        card.getStyleClass().add("seance-price-card");
        return card;
    }
}
