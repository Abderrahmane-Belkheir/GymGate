package com.GymGate.UI.controller;

import com.GymGate.UI.component.IconTile;
import com.GymGate.UI.component.MemberAvatar;
import com.GymGate.bussines.db.entities.Member;
import com.GymGate.bussines.models.MemberStats;
import com.GymGate.bussines.models.PaymentRecord;
import com.GymGate.bussines.models.Sexe;
import com.GymGate.bussines.services.I18nService;
import com.GymGate.bussines.services.MemberStatsService;
import com.GymGate.bussines.services.PhotosService;
import com.GymGate.bussines.util.Converter;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Renders one member's profile, membership summary, payment history and
 * attendance history. Hosted inside the Members screen by
 * {@link MembersController}, which supplies the action handler routing Back /
 * Renew / Cancel Membership / Delete back to its member operations.
 */
public class MemberDetailController {

    private static final String DASH = "—";
    private static final String DOT = "   ·   ";
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_YEAR_FMT =
            DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);

    @FXML private Button backButton;
    @FXML private StackPane avatarSlot;
    @FXML private Label memberNameLabel;
    @FXML private Label statusBadge;
    @FXML private Label contactLabel;
    @FXML private HBox planChip;
    @FXML private Button membershipButton;
    @FXML private Button deleteButton;
    @FXML private HBox kpiRow;
    @FXML private VBox membershipRows;
    @FXML private VBox patternRows;
    @FXML private VBox monthBars;
    @FXML private ToggleButton paymentsTab;
    @FXML private ToggleButton attendanceTab;
    @FXML private Label historyCaptionLabel;
    @FXML private StackPane historySlot;

    private final MemberStatsService statsService = MemberStatsService.getInstance();

    /** (action, member) handled by MembersController. Actions: BACK, RENEW, CANCEL_MEMBERSHIP, DELETE. */
    private BiConsumer<String, Member> actionHandler = (a, m) -> { };
    private Member current;
    private MemberStats currentStats;

    @FXML
    public void initialize() {
        backButton.setOnAction(e -> fire("BACK"));
        deleteButton.setOnAction(e -> fire("DELETE"));

        // Payments / Attendance: one always selected, table + caption swap on click.
        ToggleGroup group = paymentsTab.getToggleGroup();
        group.selectedToggleProperty().addListener((obs, was, is) -> {
            if (is == null && was != null) {
                was.setSelected(true);
            } else {
                renderHistory();
            }
        });
    }

    public void setActionHandler(BiConsumer<String, Member> handler) {
        this.actionHandler = handler != null ? handler : (a, m) -> { };
    }

    /** Loads and renders the given member. Safe to call repeatedly (after a mutation). */
    public void showMember(int memberId) {
        MemberStats stats = statsService.getStats(memberId);
        this.current = stats.getMember();
        render(stats);
    }

    private void fire(String action) {
        if (current != null || action.equals("BACK")) {
            actionHandler.accept(action, current);
        }
    }

    // ---- rendering ----

    private void render(MemberStats s) {
        Member m = s.getMember();

        MemberAvatar avatar = new MemberAvatar("member-detail-avatar", "member-detail-avatar-label");
        avatar.setInitials(initials(m));
        PhotosService.loadPhoto(m.getId())
                .ifPresentOrElse(avatar::setPhoto, () -> avatar.setPhoto(null));
        avatarSlot.getChildren().setAll(avatar);

        memberNameLabel.setText(Converter.capitalize(m.getFirstName()) + " " + Converter.capitalize(m.getLastName()));
        String phone = formatPhone(m.getPhoneNumber());
        contactLabel.setText((phone.isBlank() ? "" : phone + DOT) + genderLabel(m.getSexe())
                + DOT + I18nService.get("Member_since") + " " + fmt(m.getCreatedDate()));
        renderPlanChip(s);

        applyStatus(s.getStatus());
        configureMembershipButton(s.getStatus());

        renderKpis(s);
        renderMembershipRows(s);
        renderPatternRows(s);
        renderMonthBars(s.getVisitsByMonth());

        this.currentStats = s;
        renderHistory();
    }

    /** Builds the currently-selected history table (Payments or Attendance) into
     *  the single full-height slot, with its caption. */
    private void renderHistory() {
        if (currentStats == null) {
            return;
        }
        if (attendanceTab.isSelected()) {
            historyCaptionLabel.setText(attendanceCaption(currentStats));
            historySlot.getChildren().setAll(buildAttendanceTable(currentStats));
        } else {
            historyCaptionLabel.setText(paymentsCaption(currentStats));
            historySlot.getChildren().setAll(buildPaymentsTable(currentStats));
        }
    }

    private void renderKpis(MemberStats s) {
        kpiRow.getChildren().setAll(
                kpi("fas-money-bill-wave", "icon-tile-green",
                        money(s.getLifetimeValue()), I18nService.get("Lifetime_Value")),
                kpi("fas-receipt", "icon-tile-blue",
                        String.valueOf(s.getPaymentCount()), I18nService.get("Payments")),
                kpi("fas-walking", "icon-tile-blue",
                        String.valueOf(s.getTotalVisits()), I18nService.get("Total_Visits")),
                kpi("fas-bolt", "icon-tile-orange",
                        s.getTotalVisits() == 0 ? DASH : String.format(Locale.US, "%.1f", s.getVisitsPerWeek()),
                        I18nService.get("Visits_per_week"))
        );
    }

    private void renderMembershipRows(MemberStats s) {
        Member m = s.getMember();
        setRows(membershipRows,
                infoRow(I18nService.get("Plan"), m.getPlanName() != null && !m.getPlanName().isBlank()
                        ? m.getPlanName() : I18nService.get("No_Plan")),
                infoRow(I18nService.get("Start_Date"), m.getStartDate() != null ? fmt(m.getStartDate()) : DASH),
                infoRow(I18nService.get("End_Date"), m.getEndDate() != null ? fmt(m.getEndDate()) : DASH),
                infoRow(I18nService.get("Remaining"), remainingText(m)),
                infoRow(I18nService.get("Renewals"), String.valueOf(s.getRenewalCount()))
        );
    }

    private void renderPatternRows(MemberStats s) {
        setRows(patternRows,
                infoRow(I18nService.get("Favorite_Day"), s.getFavoriteDay() != null
                        ? Converter.capitalize(s.getFavoriteDay().getDisplayName(TextStyle.FULL, I18nService.getLocale()))
                        : DASH),
                infoRow(I18nService.get("Favorite_Hour"), s.getFavoriteHour() != null
                        ? String.format("%02d:00", s.getFavoriteHour()) : DASH),
                infoRow(I18nService.get("Last_Visit"), s.getLastVisitDate() != null
                        ? fmt(s.getLastVisitDate()) : I18nService.get("Never")),
                infoRow(I18nService.get("Days_since_last_visit"), s.getDaysSinceLastVisit() != null
                        ? String.valueOf(s.getDaysSinceLastVisit()) : DASH),
                infoRow(I18nService.get("Longest_Gap"), s.getTotalVisits() < 2
                        ? DASH : s.getLongestGapDays() + " " + I18nService.get("days"))
        );
    }

    /** Places definition rows and flushes the first one's top hairline. */
    private void setRows(VBox container, HBox... rows) {
        container.getChildren().setAll(rows);
        if (rows.length > 0) {
            rows[0].getStyleClass().add("detail-row-flush");
        }
    }

    private void applyStatus(MemberStats.MembershipStatus status) {
        String key;
        String style;
        switch (status) {
            case ACTIVE -> { key = "Active"; style = "badge-active"; }
            case EXPIRING -> { key = "Expiring"; style = "badge-warning"; }
            case EXPIRED -> { key = "Expired"; style = "badge-danger"; }
            default -> { key = "No_Active_Plan"; style = "badge-no"; }
        }
        statusBadge.getStyleClass().setAll("badge", style);
        statusBadge.setText(I18nService.get(key));
    }

    private void configureMembershipButton(MemberStats.MembershipStatus status) {
        boolean renew = status == MemberStats.MembershipStatus.EXPIRED
                || status == MemberStats.MembershipStatus.NO_PLAN;

        membershipButton.getStyleClass().setAll("pill-action-button");
        if (renew) {
            membershipButton.getStyleClass().add("pill-action-primary");
        }

        FontIcon icon = new FontIcon(renew ? "fas-sync-alt" : "fas-ban");
        icon.getStyleClass().add(renew ? "pill-icon-primary" : "pill-icon");
        Label label = new Label(I18nService.get(renew ? "Renew" : "Cancel_Membership"));
        label.getStyleClass().add(renew ? "pill-label-primary" : "pill-label");
        HBox graphic = new HBox(8, icon, label);
        graphic.setAlignment(Pos.CENTER);
        membershipButton.setGraphic(graphic);
        membershipButton.setOnAction(e -> fire(renew ? "RENEW" : "CANCEL_MEMBERSHIP"));
    }

    /** The current-plan chip in the identity band: icon + plan name (strong) +
     *  muted term details (days left · ends <date>). */
    private void renderPlanChip(MemberStats s) {
        Member m = s.getMember();
        planChip.getChildren().clear();

        FontIcon icon = new FontIcon("fas-id-card");
        icon.getStyleClass().add("member-detail-plan-icon");
        planChip.getChildren().add(icon);

        if (m.getPlanId() == null) {
            Label none = new Label(I18nService.get("No_Active_Plan"));
            none.getStyleClass().add("member-detail-plan-meta");
            planChip.getChildren().add(none);
            return;
        }

        Label name = new Label(m.getPlanName() != null && !m.getPlanName().isBlank()
                ? m.getPlanName() : I18nService.get("Plan"));
        name.getStyleClass().add("member-detail-plan-name");
        planChip.getChildren().add(name);

        List<String> parts = new ArrayList<>();
        String remaining = remainingText(m);
        if (!remaining.equals(DASH)) {
            parts.add(remaining);
        }
        if (m.getEndDate() != null) {
            String verb = s.getStatus() == MemberStats.MembershipStatus.EXPIRED
                    ? I18nService.get("Expired").toLowerCase(I18nService.getLocale())
                    : I18nService.get("ends");
            parts.add(verb + " " + fmt(m.getEndDate()));
        }
        for (String part : parts) {
            Label sep = new Label("·");
            sep.getStyleClass().add("member-detail-plan-sep");
            Label meta = new Label(part);
            meta.getStyleClass().add("member-detail-plan-meta");
            planChip.getChildren().addAll(sep, meta);
        }
    }

    // ---- small builders ----

    private Region kpi(String iconLiteral, String tileClass, String value, String label) {
        IconTile tile = new IconTile(iconLiteral, tileClass, 15);

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("member-detail-kpi-value");
        Label captionLabel = new Label(label);
        captionLabel.getStyleClass().add("member-detail-kpi-label");
        VBox text = new VBox(1, valueLabel, captionLabel);
        text.setAlignment(Pos.CENTER_LEFT);

        HBox card = new HBox(13, tile, text);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("member-detail-kpi");
        card.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private HBox infoRow(String label, String value) {
        Label l = new Label(label);
        l.getStyleClass().add("detail-row-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label v = new Label(value);
        v.getStyleClass().add("detail-row-value");
        if (value.equals(DASH)) {
            v.getStyleClass().add("detail-row-value-muted");
        }
        HBox row = new HBox(l, spacer, v);
        row.getStyleClass().add("detail-row");
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private void renderMonthBars(Map<YearMonth, Integer> byMonth) {
        monthBars.getChildren().clear();
        int max = byMonth.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        for (Map.Entry<YearMonth, Integer> e : byMonth.entrySet()) {
            boolean peak = max > 0 && e.getValue() == max;

            Label month = new Label(e.getKey().getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            month.getStyleClass().add("mini-bar-month");
            month.setMinWidth(38);

            StackPane track = new StackPane();
            track.getStyleClass().add("mini-bar-track");
            track.setAlignment(Pos.CENTER_LEFT);
            track.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(track, Priority.ALWAYS);

            Region fill = new Region();
            fill.getStyleClass().add("mini-bar-fill");
            if (peak) {
                fill.getStyleClass().add("mini-bar-fill-peak");
            }
            double frac = max == 0 ? 0 : (double) e.getValue() / max;
            if (e.getValue() > 0) {
                frac = Math.max(frac, 0.06);
            }
            fill.maxWidthProperty().bind(track.widthProperty().multiply(frac));
            fill.prefWidthProperty().bind(fill.maxWidthProperty());
            track.getChildren().add(fill);

            Label count = new Label(String.valueOf(e.getValue()));
            count.getStyleClass().add("mini-bar-count");
            if (peak) {
                count.getStyleClass().add("mini-bar-count-peak");
            }
            count.setMinWidth(22);
            count.setAlignment(Pos.CENTER_RIGHT);

            HBox row = new HBox(8, month, track, count);
            row.getStyleClass().add("mini-bar-row");
            row.setAlignment(Pos.CENTER_LEFT);
            monthBars.getChildren().add(row);
        }
    }

    private String paymentsCaption(MemberStats s) {
        if (s.getPayments().isEmpty()) {
            return "";
        }
        String count = s.getPaymentCount() + " "
                + I18nService.get(s.getPaymentCount() == 1 ? "Payment" : "Payments");
        String span = "";
        if (s.getFirstPaymentDate() != null && s.getLastPaymentDate() != null) {
            String from = s.getFirstPaymentDate().format(MONTH_YEAR_FMT);
            String to = s.getLastPaymentDate().format(MONTH_YEAR_FMT);
            span = from.equals(to) ? from : from + " – " + to;
        }
        return span.isEmpty() ? count : count + DOT + span;
    }

    private TableView<PaymentRecord> buildPaymentsTable(MemberStats s) {
        TableView<PaymentRecord> table = historyTable(I18nService.get("No_payments_yet"));

        TableColumn<PaymentRecord, String> dateCol = column(I18nService.get("Date"),
                PaymentRecord::getFormattedDate, Col.PRIMARY);
        dateCol.setMinWidth(130);
        dateCol.setMaxWidth(190);
        TableColumn<PaymentRecord, String> timeCol = column(I18nService.get("Time"),
                PaymentRecord::getFormattedTime, Col.TEXT);
        timeCol.setMinWidth(78);
        timeCol.setMaxWidth(110);
        TableColumn<PaymentRecord, String> planCol = column(I18nService.get("Plan"),
                PaymentRecord::getPlanName, Col.TEXT);
        planCol.setMinWidth(120);
        TableColumn<PaymentRecord, String> amountCol = column(I18nService.get("Amount"),
                r -> money(r.getAmount()), Col.VALUE);
        amountCol.setMinWidth(130);
        amountCol.setMaxWidth(180);

        table.getColumns().setAll(List.of(dateCol, timeCol, planCol, amountCol));
        table.setItems(FXCollections.observableArrayList(s.getPayments()));
        return table;
    }

    private String attendanceCaption(MemberStats s) {
        if (s.getVisits().isEmpty()) {
            return "";
        }
        String count = s.getTotalVisits() + " "
                + I18nService.get(s.getTotalVisits() == 1 ? "Visit" : "Visits");
        String since = s.getFirstVisitDate() != null
                ? DOT + I18nService.get("Since") + " " + fmt(s.getFirstVisitDate())
                : "";
        return count + since;
    }

    /** One attendance row: the check-in plus the gap, in days, from the
     *  previous (older) check-in ({@code -1} for the very first visit). */
    private record VisitRow(LocalDateTime at, int gapDays) { }

    private TableView<VisitRow> buildAttendanceTable(MemberStats s) {
        List<LocalDateTime> asc = s.getVisits();
        List<VisitRow> rows = new ArrayList<>();
        for (int i = asc.size() - 1; i >= 0; i--) {
            int gap = i == 0 ? -1
                    : (int) ChronoUnit.DAYS.between(asc.get(i - 1).toLocalDate(), asc.get(i).toLocalDate());
            rows.add(new VisitRow(asc.get(i), gap));
        }

        TableView<VisitRow> table = historyTable(I18nService.get("No_visits_yet"));

        TableColumn<VisitRow, String> dateCol = column(I18nService.get("Date"),
                r -> r.at().toLocalDate().format(DATE_FMT), Col.PRIMARY);
        dateCol.setMinWidth(130);
        dateCol.setMaxWidth(190);
        TableColumn<VisitRow, String> dayCol = column(I18nService.get("Day"),
                r -> Converter.capitalize(r.at().getDayOfWeek().getDisplayName(TextStyle.FULL, I18nService.getLocale())),
                Col.TEXT);
        dayCol.setMinWidth(110);
        TableColumn<VisitRow, String> timeCol = column(I18nService.get("Time"),
                r -> r.at().toLocalTime().format(TIME_FMT), Col.TEXT);
        timeCol.setMinWidth(80);
        timeCol.setMaxWidth(110);
        TableColumn<VisitRow, String> gapCol = column(I18nService.get("Gap"),
                r -> r.gapDays() < 0 ? DASH : String.valueOf(r.gapDays()), Col.VALUE);
        gapCol.setMinWidth(80);
        gapCol.setMaxWidth(120);

        table.getColumns().setAll(List.of(dateCol, dayCol, timeCol, gapCol));
        table.setItems(FXCollections.observableArrayList(rows));
        return table;
    }

    /** A history grid that fills its slot top-to-bottom and scrolls internally
     *  once the rows exceed the visible height. */
    private <T> TableView<T> historyTable(String placeholder) {
        TableView<T> table = new TableView<>();
        table.getStyleClass().addAll("attendance-table", "members-table", "member-detail-table");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        Label empty = new Label(placeholder);
        empty.getStyleClass().add("member-detail-empty");
        table.setPlaceholder(empty);
        table.setPrefHeight(280);
        table.setMinHeight(180);
        table.setMaxHeight(Double.MAX_VALUE);
        table.setFocusTraversable(false);
        return table;
    }

    /** Visual weight of a history column: {@code PRIMARY} anchors the row,
     *  {@code TEXT} is supporting detail, {@code VALUE} is an emphasised number. */
    private enum Col {
        PRIMARY("cell-primary"),
        TEXT("cell-text"),
        VALUE("cell-value");

        private final String cellClass;

        Col(String cellClass) {
            this.cellClass = cellClass;
        }
    }

    private <T> TableColumn<T, String> column(String title,
                                              java.util.function.Function<T, String> accessor,
                                              Col kind) {
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setSortable(false);
        col.setCellValueFactory(d -> new SimpleStringProperty(safe(accessor.apply(d.getValue()))));
        col.getStyleClass().add(kind == Col.VALUE ? "col-num" : "col-text");
        col.setCellFactory(c -> {
            TableCell<T, String> cell = new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                }
            };
            cell.getStyleClass().add(kind.cellClass);
            cell.setAlignment(Pos.CENTER);
            return cell;
        });
        return col;
    }

    // ---- formatting helpers ----

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String fmt(LocalDate date) {
        return date == null ? DASH : date.format(DATE_FMT);
    }

    private String remainingText(Member m) {
        if (m.getPlanId() == null) {
            return DASH;
        }
        if (m.getRemainingDays() == null) {
            return I18nService.get("Unlimited");
        }
        return m.getRemainingDays() + " " + I18nService.get("days_left");
    }

    private static String genderLabel(Sexe sexe) {
        return sexe == Sexe.FEMALE ? I18nService.get("Female") : I18nService.get("Male");
    }

    /** Display-only grouping of a 10-digit local mobile ("0X XX XX XX XX"); any
     *  other shape is shown untouched. */
    private static String formatPhone(String phone) {
        if (phone == null) {
            return "";
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.length() == 10 && digits.startsWith("0")) {
            return digits.substring(0, 2) + " " + digits.substring(2, 4) + " "
                    + digits.substring(4, 6) + " " + digits.substring(6, 8) + " " + digits.substring(8);
        }
        return phone;
    }

    private static String money(int amount) {
        return String.format(Locale.US, "%,d DZD", amount);
    }

    private static String initials(Member m) {
        String a = m.getFirstName() != null && !m.getFirstName().isBlank()
                ? m.getFirstName().trim().substring(0, 1) : "";
        String b = m.getLastName() != null && !m.getLastName().isBlank()
                ? m.getLastName().trim().substring(0, 1) : "";
        return (a + b).toUpperCase();
    }
}
