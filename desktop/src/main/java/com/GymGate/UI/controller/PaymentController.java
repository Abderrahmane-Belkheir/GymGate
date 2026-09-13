package com.GymGate.UI.controller;

import com.GymGate.UI.component.MemberAvatar;
import com.GymGate.UI.component.TrendBarChart;
import com.GymGate.bussines.db.dao.PaymentDao;
import com.GymGate.bussines.models.PaymentRecord;
import com.GymGate.bussines.models.Sexe;
import com.GymGate.bussines.services.AppEvents;
import com.GymGate.bussines.services.I18nService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;

import static com.GymGate.bussines.services.PhotosService.loadPhoto;

/**
 * Controller for the Payments page (PaymentsView.fxml).
 * Lets the user pick a year/month/day, press Confirm to load that day's
 * payment history, and renders it in a multi-column TableView styled to match
 * the Members and Attendance tables (identity, plan, date, time, amount).
 */
public class PaymentController {

    @FXML private ComboBox<Integer> yearCombo;
    @FXML private ComboBox<String> monthCombo;
    @FXML private ComboBox<Integer> dayCombo;
    @FXML private Button confirmButton;

    @FXML private ToggleButton listTab;
    @FXML private ToggleButton statsTab;
    @FXML private HBox granularityBar;
    @FXML private ToggleButton monthTab;
    @FXML private ToggleButton yearTab;
    @FXML private ToggleButton allTab;
    @FXML private StackPane listPane;
    @FXML private VBox statsPane;
    @FXML private StackPane statsSlot;
    @FXML private Label statsTitleLabel;
    @FXML private Label statsHintLabel;

    @FXML private Label revenueLabel;
    @FXML private Label paymentCountLabel;
    @FXML private TableView<PaymentRecord> paymentsTable;
    @FXML private TableColumn<PaymentRecord, PaymentRecord> paymentColumn;
    @FXML private TableColumn<PaymentRecord, PaymentRecord> planColumn;
    @FXML private TableColumn<PaymentRecord, PaymentRecord> dateColumn;
    @FXML private TableColumn<PaymentRecord, PaymentRecord> whenColumn;
    @FXML private TableColumn<PaymentRecord, PaymentRecord> amountColumn;

    private final ObservableList<PaymentRecord> tableItems = FXCollections.observableArrayList();

    private final PaymentDao paymentService=PaymentDao.getInstance() ;

    /** A payment arrived while this screen was off-view; re-run the query when
     *  it's shown again rather than on every background registration/renewal. */
    private boolean pendingReload;

    @FXML
    public void initialize() {
        populateDateSelectors();
        setupTable();
        confirmButton.setOnAction(e -> onConfirm());

        preventEmptySelection(listTab.getToggleGroup());
        preventEmptySelection(monthTab.getToggleGroup());
        listTab.getToggleGroup().selectedToggleProperty().addListener((o, was, is) -> {
            if (is != null) {
                applyView();
            }
        });
        monthTab.getToggleGroup().selectedToggleProperty().addListener((o, was, is) -> {
            if (is != null && statsTab.isSelected()) {
                updateDateSelectors();
                renderStats();
            }
        });

        applyView();

        // A registration or renewal elsewhere records a payment — reflect it here
        // immediately, but only if this screen is on view; otherwise defer the
        // reload to the next time it's shown.
        AppEvents.subscribe(AppEvents.Type.PAYMENT_ADDED, () -> {
            if (paymentsTable.getScene() == null) {
                pendingReload = true;
            } else {
                onConfirm();
            }
        });

        paymentsTable.sceneProperty().addListener((obs, was, is) -> {
            if (is != null && pendingReload) {
                pendingReload = false;
                onConfirm();
            }
        });
    }

    private void updateRevenueLabel(int year,int month,int day) {
        revenueLabel.setText(formatAmount(paymentService.sumAmount(year, month, day)));
    }

    private void updateCountLabel() {
        int count = tableItems.size();
        paymentCountLabel.setText(count + " " + I18nService.get(count == 1 ? "Payment" : "Payments"));
    }

    /** Groups thousands and appends the currency, e.g. 12500 -> "12,500 DZD". */
    private static String formatAmount(long amount) {
        return String.format(Locale.US, "%,d DZD", amount);
    }

    private void setupTable() {
        paymentsTable.setItems(tableItems);
        paymentsTable.setPlaceholder(new Label(I18nService.get("No_payments_for_this_date.")));

        paymentColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        paymentColumn.setCellFactory(col -> new MemberCell());

        planColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        planColumn.setCellFactory(col -> new PlanCell());

        dateColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        dateColumn.setCellFactory(col -> new DateCell());

        whenColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        whenColumn.setCellFactory(col -> new TimeCell());

        amountColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        amountColumn.setCellFactory(col -> new AmountCell());
    }

    private void populateDateSelectors() {
        LocalDate today = LocalDate.now();

        int currentYear = today.getYear();
        for (int year = currentYear - 2; year <= currentYear + 1; year++) {
            yearCombo.getItems().add(year);
        }
        yearCombo.setValue(currentYear);

        for (Month month : Month.values()) {
            monthCombo.getItems().add(month.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        }
        monthCombo.setValue(today.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH));

        for (int day = 1; day <= 31; day++) {
            dayCombo.getItems().add(day);
        }
        dayCombo.setValue(today.getDayOfMonth());
    }

    /** Confirm button + PAYMENT_ADDED — refresh whichever view is showing. */
    private void onConfirm() {
        if (statsTab != null && statsTab.isSelected()) {
            renderStats();
        } else {
            loadList();
        }
    }

    private void loadList() {
        int year = yearCombo.getValue();
        int month = Month.valueOf(monthCombo.getValue().toUpperCase()).getValue();
        int day = dayCombo.getValue();

        List<PaymentRecord> records = paymentService.find(year, month, day);
        tableItems.setAll(records);
        updateRevenueLabel(year, month, day);
        updateCountLabel();
    }

    // ---- List / Statistics switch ----

    private static void preventEmptySelection(ToggleGroup group) {
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });
    }

    private void applyView() {
        boolean stats = statsTab.isSelected();

        listPane.setVisible(!stats);
        listPane.setManaged(!stats);
        statsPane.setVisible(stats);
        statsPane.setManaged(stats);

        granularityBar.setVisible(stats);
        granularityBar.setManaged(stats);
        paymentCountLabel.setVisible(!stats);
        paymentCountLabel.setManaged(!stats);

        updateDateSelectors();
        onConfirm();
    }

    /**
     * Shows only the date pickers the current view actually uses:
     * list → year + month + day; stats/month → year + month; stats/year → year;
     * stats/all-time → none.
     */
    private void updateDateSelectors() {
        boolean stats = statsTab.isSelected();
        boolean showYear = !stats || !allTab.isSelected();
        boolean showMonth = !stats || monthTab.isSelected();
        boolean showDay = !stats;

        setShown(yearCombo, showYear);
        setShown(monthCombo, showMonth);
        setShown(dayCombo, showDay);
    }

    private static void setShown(Node node, boolean shown) {
        node.setVisible(shown);
        node.setManaged(shown);
    }

    // ---- Statistics ----

    private void renderStats() {
        LocalDate today = LocalDate.now();
        int year = yearCombo.getValue();
        int month = Month.valueOf(monthCombo.getValue().toUpperCase()).getValue();

        List<String> cats = new ArrayList<>();
        List<Integer> vals = new ArrayList<>();
        String title;
        int currentIndex;

        if (yearTab.isSelected()) {
            Map<Integer, Integer> byMonth = paymentService.revenueByMonth(year);
            for (int m = 1; m <= 12; m++) {
                cats.add(capitalize(Month.of(m).getDisplayName(TextStyle.SHORT, I18nService.getLocale())));
                vals.add(byMonth.getOrDefault(m, 0));
            }
            title = I18nService.get("Monthly_revenue") + "  ·  " + year;
            currentIndex = year == today.getYear() ? today.getMonthValue() - 1 : -1;

        } else if (allTab.isSelected()) {
            SortedMap<Integer, Integer> byYear = paymentService.revenueByYear();
            for (Map.Entry<Integer, Integer> e : byYear.entrySet()) {
                cats.add(String.valueOf(e.getKey()));
                vals.add(e.getValue());
            }
            title = I18nService.get("Yearly_revenue");
            // No "in progress" fade here — a whole current year rendered pale
            // just looks weak next to two or three solid bars.
            currentIndex = -1;

        } else {
            Map<Integer, Integer> byDay = paymentService.revenueByDay(year, month);
            int len = YearMonth.of(year, month).lengthOfMonth();
            for (int d = 1; d <= len; d++) {
                cats.add(String.valueOf(d));
                vals.add(byDay.getOrDefault(d, 0));
            }
            title = I18nService.get("Daily_revenue") + "  ·  "
                    + capitalize(Month.of(month).getDisplayName(TextStyle.FULL, I18nService.getLocale()))
                    + " " + year;
            currentIndex = year == today.getYear() && month == today.getMonthValue()
                    ? today.getDayOfMonth() - 1 : -1;
        }

        TrendBarChart.Stats summary = TrendBarChart.summarize(vals);
        statsTitleLabel.setText(title);
        statsHintLabel.setText(hint(summary));
        revenueLabel.setText(formatAmount(summary.total()));
        statsSlot.getChildren().setAll(TrendBarChart.build(
                cats, vals, summary, currentIndex, "DZD", I18nService.get("No_revenue_data")));
    }

    /** "Average 12,400   ·   Peak 45,000" — the reference figures beside the title. */
    private static String hint(TrendBarChart.Stats s) {
        if (s.total() == 0) {
            return "";
        }
        return I18nService.get("Average") + " " + String.format(Locale.US, "%,d", Math.round(s.average()))
                + "     ·     " + I18nService.get("Peak") + " " + String.format(Locale.US, "%,d", s.peak());
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** First letter of the first two words of a display name, for the avatar fallback. */
    private static String initialsOf(String fullName) {
        if (fullName == null || fullName.isBlank()) {
            return "";
        }
        String[] parts = fullName.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && sb.length() < 2; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return sb.toString();
    }

    /** IDENTITY group (paymentColumn): avatar + member name. */
    private static class MemberCell extends TableCell<PaymentRecord, PaymentRecord> {
        MemberCell() {
            setAlignment(Pos.CENTER_LEFT);
        }

        @Override
        protected void updateItem(PaymentRecord record, boolean empty) {
            super.updateItem(record, empty);
            if (empty || record == null) {
                setGraphic(null);
                return;
            }

            if (record.isSeance()) {
                FontIcon glyph = new FontIcon("fas-ticket-alt");
                glyph.getStyleClass().add("payments-seance-glyph");
                StackPane badge = new StackPane(glyph);
                badge.getStyleClass().add("payments-seance-badge");

                Label label = new Label(I18nService.get(
                        record.getSeanceSexe() == Sexe.MALE ? "Male" : "Female"));
                label.getStyleClass().add("member-row-name");

                HBox content = new HBox(13, badge, label);
                content.setAlignment(Pos.CENTER_LEFT);
                setGraphic(content);
                return;
            }

            MemberAvatar avatar = new MemberAvatar("attendance-avatar", "attendance-avatar-label");
            String initials = record.getInitials();
            avatar.setInitials(initials == null || initials.isBlank()
                    ? initialsOf(record.getFullName())
                    : initials);
            loadPhoto(record.getId()).ifPresentOrElse(avatar::setPhoto, () -> avatar.setPhoto(null));

            Label name = new Label(record.getFullName());
            name.getStyleClass().add("member-row-name");

            HBox content = new HBox(13, avatar, name);
            content.setAlignment(Pos.CENTER_LEFT);
            setGraphic(content);
        }
    }

    /** The plan this payment was for, with a small membership-card icon. */
    private static class PlanCell extends TableCell<PaymentRecord, PaymentRecord> {
        PlanCell() {
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(PaymentRecord record, boolean empty) {
            super.updateItem(record, empty);
            if (empty || record == null) {
                setGraphic(null);
                return;
            }

            String iconLiteral;
            String text;
            if (record.isSeance()) {
                iconLiteral = record.isSeanceCardio() ? "fas-heartbeat" : "fas-dumbbell";
                text = I18nService.get(record.isSeanceCardio() ? "With_cardio" : "Standard");
            } else {
                iconLiteral = "fas-id-card";
                text = record.getPlanName();
            }

            FontIcon icon = new FontIcon(iconLiteral);
            icon.getStyleClass().add("payments-cell-icon");
            Label label = new Label(text);
            label.getStyleClass().add("payments-plan-name");
            HBox row = new HBox(6, icon, label);
            row.setAlignment(Pos.CENTER);
            setGraphic(row);
        }
    }

    /** Payment date (day-level). */
    private static class DateCell extends TableCell<PaymentRecord, PaymentRecord> {
        DateCell() {
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(PaymentRecord record, boolean empty) {
            super.updateItem(record, empty);
            if (empty || record == null) {
                setGraphic(null);
                return;
            }

            Label label = new Label(record.getFormattedDate());
            label.getStyleClass().add("payments-cell-value");
            setGraphic(label);
        }
    }

    /** Time of day the payment was taken. */
    private static class TimeCell extends TableCell<PaymentRecord, PaymentRecord> {
        TimeCell() {
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(PaymentRecord record, boolean empty) {
            super.updateItem(record, empty);
            if (empty || record == null) {
                setGraphic(null);
                return;
            }

            Label label = new Label(record.getFormattedTime());
            label.getStyleClass().add("payments-cell-value");
            setGraphic(label);
        }
    }

    /** Amount paid — the terminal value in the row. */
    private static class AmountCell extends TableCell<PaymentRecord, PaymentRecord> {
        AmountCell() {
            setAlignment(Pos.CENTER);
        }

        @Override
        protected void updateItem(PaymentRecord record, boolean empty) {
            super.updateItem(record, empty);
            if (empty || record == null) {
                setGraphic(null);
                return;
            }

            Label amount = new Label(formatAmount(record.getAmount()));
            amount.getStyleClass().add("payments-amount-value");
            setGraphic(amount);
        }
    }
}
