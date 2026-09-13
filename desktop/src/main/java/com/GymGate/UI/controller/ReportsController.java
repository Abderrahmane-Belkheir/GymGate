package com.GymGate.UI.controller;

import com.GymGate.UI.component.IconTile;
import com.GymGate.UI.component.MemberAvatar;
import com.GymGate.UI.component.TrendStackedBarChart;
import com.GymGate.bussines.db.entities.Member;
import com.GymGate.bussines.services.I18nService;
import com.GymGate.bussines.services.ReportsService;
import com.GymGate.bussines.services.ReportsService.Snapshot;
import com.GymGate.bussines.util.Converter;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.GymGate.bussines.services.PhotosService.loadPhoto;

/**
 * The Reports / Insights page. The period switch drives the KPIs, the by-plan
 * breakdown and the renewals-due list; the new-vs-renewal trend is a fixed
 * rolling 12 months. All computation lives in {@link ReportsService}; this
 * controller only shapes the results into the GymGate visual language.
 */
public class ReportsController {

    private static final int MAX_RENEWAL_ROWS = 5;

    @FXML private ToggleButton thisMonthTab;
    @FXML private ToggleButton lastMonthTab;
    @FXML private ToggleButton last3Tab;
    @FXML private ToggleButton yearTab;

    @FXML private Label rangeCaption;
    @FXML private HBox kpiRow;
    @FXML private StackPane trendSlot;
    @FXML private VBox planBars;
    @FXML private Label memberTotal;
    @FXML private VBox compositionRows;
    @FXML private Label dueSummary;
    @FXML private VBox renewalsList;

    private final ReportsService reports = ReportsService.getInstance();

    @FXML
    public void initialize() {
        ToggleGroup group = thisMonthTab.getToggleGroup();
        group.selectedToggleProperty().addListener((o, was, is) -> {
            if (is == null && was != null) {
                was.setSelected(true);
            } else if (is != null) {
                render();
            }
        });
        render();
    }

    private ReportsService.Period currentPeriod() {
        if (lastMonthTab.isSelected()) {
            return ReportsService.Period.LAST_MONTH;
        }
        if (last3Tab.isSelected()) {
            return ReportsService.Period.LAST_3_MONTHS;
        }
        if (yearTab.isSelected()) {
            return ReportsService.Period.THIS_YEAR;
        }
        return ReportsService.Period.THIS_MONTH;
    }

    private void render() {
        Snapshot s = reports.build(currentPeriod());
        rangeCaption.setText(rangeText(s));
        renderKpis(s);
        renderTrend(s);
        renderPlanBars(s);
        renderComposition(s);
        renderRenewals(s);
    }

    // ---- range caption ----

    private String rangeText(Snapshot s) {
        Locale loc = I18nService.getLocale();
        DateTimeFormatter dm = DateTimeFormatter.ofPattern("d MMM", loc);
        DateTimeFormatter dmy = DateTimeFormatter.ofPattern("d MMM yyyy", loc);
        String cur = s.range().from().format(dm) + " – " + s.range().to().format(dmy);
        String prev = s.compare().from().format(dm) + " – " + s.compare().to().format(dmy);
        return cur + "     " + I18nService.get("vs") + "  " + prev;
    }

    // ---- KPIs ----

    private void renderKpis(Snapshot s) {
        kpiRow.getChildren().setAll(
                revenueKpi(s),
                kpi("fas-user-plus", "icon-tile-blue", I18nService.get("New_Members"),
                        num(s.newMembers()), delta(s.newMembers(), s.newMembersPrev())),
                kpi("fas-sync-alt", "icon-tile-green", I18nService.get("Renewals"),
                        num(s.renewals()), delta(s.renewals(), s.renewalsPrev())),
                kpi("fas-sign-in-alt", "icon-tile-blue", I18nService.get("Visits"),
                        num(s.visits()), delta(s.visits(), s.visitsPrev())),
                kpi("fas-users", "icon-tile-blue", I18nService.get("Active_Members"),
                        num(s.activeMembers()), null));
    }

    /** Double-width KPI: total revenue on the left, séance revenue on the right. */
    private Region revenueKpi(Snapshot s) {
        VBox total = kpiBody("fas-dollar-sign", "icon-tile-green", I18nService.get("Revenue"),
                money(s.revenue()), delta(s.revenue(), s.revenuePrev()));
        VBox seance = kpiBody("fas-ticket-alt", "icon-tile-green", I18nService.get("Single_Session"),
                money(s.seanceRevenue()), delta(s.seanceRevenue(), s.seanceRevenuePrev()));
        HBox.setHgrow(total, Priority.ALWAYS);
        HBox.setHgrow(seance, Priority.ALWAYS);

        Region divider = new Region();
        divider.getStyleClass().add("reports-kpi-divider");
        divider.setMaxHeight(Double.MAX_VALUE);

        HBox inner = new HBox(16, total, divider, seance);
        inner.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(inner);
        card.getStyleClass().addAll("reports-kpi", "reports-kpi-wide");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private Region kpi(String icon, String tile, String label, String valueText, Long deltaPct) {
        VBox card = kpiBody(icon, tile, label, valueText, deltaPct);
        card.getStyleClass().add("reports-kpi");
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    /** The icon + value + label + optional delta chip, without the card chrome. */
    private VBox kpiBody(String icon, String tile, String label, String valueText, Long deltaPct) {
        IconTile iconTile = new IconTile(icon, tile, 15);

        Label value = new Label(valueText);
        value.getStyleClass().add("reports-kpi-value");
        Label name = new Label(label);
        name.getStyleClass().add("reports-kpi-label");
        VBox text = new VBox(1, value, name);

        HBox top = new HBox(11, iconTile, text);
        top.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(9, top);
        if (deltaPct != null) {
            boolean up = deltaPct >= 0;
            FontIcon arrow = new FontIcon(up ? "fas-arrow-up" : "fas-arrow-down");
            arrow.getStyleClass().add("reports-kpi-delta-icon");
            Label d = new Label(Math.abs(deltaPct) + "%");
            HBox chip = new HBox(4, arrow, d);
            chip.setAlignment(Pos.CENTER_LEFT);
            chip.getStyleClass().addAll("reports-kpi-delta", up ? "reports-delta-up" : "reports-delta-down");
            body.getChildren().add(chip);
        }
        return body;
    }

    private static Long delta(long cur, long prev) {
        if (prev <= 0) {
            return null;
        }
        return Math.round((cur - prev) * 100.0 / prev);
    }

    // ---- new vs renewal revenue trend ----

    private void renderTrend(Snapshot s) {
        Locale loc = I18nService.getLocale();
        List<String> cats = new java.util.ArrayList<>();
        List<int[]> rows = new java.util.ArrayList<>();
        for (Map.Entry<YearMonth, int[]> e : s.revenueTrend().entrySet()) {
            cats.add(Converter.capitalize(e.getKey().getMonth().getDisplayName(TextStyle.SHORT, loc)));
            rows.add(e.getValue());
        }
        trendSlot.getChildren().setAll(TrendStackedBarChart.build(
                cats, rows,
                I18nService.get("Registrations"), I18nService.get("Renewals"),
                this::money));
    }

    // ---- revenue by plan ----

    private void renderPlanBars(Snapshot s) {
        planBars.getChildren().clear();
        Map<String, Integer> byPlan = s.revenueByPlan();
        if (byPlan.isEmpty()) {
            planBars.getChildren().setAll(emptyLabel());
            return;
        }
        int max = byPlan.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        boolean first = true;
        for (Map.Entry<String, Integer> e : byPlan.entrySet()) {
            FontIcon icon = new FontIcon("fas-id-card");
            icon.getStyleClass().add("reports-bar-icon");
            Label name = new Label(e.getKey());
            name.getStyleClass().add("reports-bar-name");
            HBox nameRow = new HBox(7, icon, name);
            nameRow.setAlignment(Pos.CENTER_LEFT);

            Label val = new Label(money(e.getValue()));
            val.getStyleClass().add("reports-bar-value");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox top = new HBox(8, nameRow, spacer, val);
            top.setAlignment(Pos.CENTER_LEFT);

            Region fill = new Region();
            fill.getStyleClass().add("reports-bar-fill");
            if (first) {
                fill.getStyleClass().add("reports-bar-fill-lead");
            }
            fill.setMaxWidth(Region.USE_PREF_SIZE);
            StackPane track = new StackPane(fill);
            track.getStyleClass().add("reports-bar-track");
            StackPane.setAlignment(fill, Pos.CENTER_LEFT);
            double frac = max == 0 ? 0 : Math.max(0.04, e.getValue() / (double) max);
            fill.prefWidthProperty().bind(track.widthProperty().multiply(frac));

            planBars.getChildren().add(new VBox(6, top, track));
            first = false;
        }
    }

    // ---- membership composition ----

    private void renderComposition(Snapshot s) {
        int[] c = s.composition();
        int total = c[0] + c[1] + c[2] + c[3];
        memberTotal.setText(total + " " + I18nService.get(total == 1 ? "Member" : "Members"));

        HBox segments = new HBox();
        segments.getStyleClass().add("reports-composition-bar");
        addSegment(segments, c[0], total, "reports-seg-active");
        addSegment(segments, c[1], total, "reports-seg-expiring");
        addSegment(segments, c[2], total, "reports-seg-expired");
        addSegment(segments, c[3], total, "reports-seg-noplan");

        compositionRows.getChildren().setAll(
                segments,
                compRow("reports-dot-active", I18nService.get("Active"), c[0], total),
                compRow("reports-dot-expiring", I18nService.get("Expiring"), c[1], total),
                compRow("reports-dot-expired", I18nService.get("Expired"), c[2], total),
                compRow("reports-dot-noplan", I18nService.get("No_Plan"), c[3], total));
    }

    private void addSegment(HBox bar, int value, int total, String styleClass) {
        if (value <= 0 || total <= 0) {
            return;
        }
        Region seg = new Region();
        seg.getStyleClass().addAll("reports-seg", styleClass);
        seg.prefWidthProperty().bind(bar.widthProperty().multiply(value / (double) total));
        bar.getChildren().add(seg);
    }

    private Region compRow(String dotClass, String label, int count, int total) {
        Region dot = new Region();
        dot.getStyleClass().addAll("reports-dot", dotClass);
        Label name = new Label(label);
        name.getStyleClass().add("reports-comp-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label pct = new Label(total == 0 ? "0%" : Math.round(count * 100.0 / total) + "%");
        pct.getStyleClass().add("reports-comp-pct");
        Label c = new Label(String.valueOf(count));
        c.getStyleClass().add("reports-comp-count");
        HBox row = new HBox(9, dot, name, spacer, pct, c);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("reports-comp-row");
        VBox.setVgrow(row, Priority.ALWAYS);
        return row;
    }

    // ---- renewals due ----

    private void renderRenewals(Snapshot s) {
        dueSummary.setText("7d " + s.dueIn7() + "   ·   14d " + s.dueIn14() + "   ·   30d " + s.dueIn30());
        renewalsList.getChildren().clear();
        List<Member> due = s.renewalsDue();
        if (due.isEmpty()) {
            renewalsList.getChildren().setAll(emptyLabel());
            return;
        }
        Locale loc = I18nService.getLocale();
        DateTimeFormatter dmy = DateTimeFormatter.ofPattern("d MMM yyyy", loc);
        LocalDate today = LocalDate.now();
        int shown = Math.min(due.size(), MAX_RENEWAL_ROWS);
        for (int i = 0; i < shown; i++) {
            Member m = due.get(i);

            MemberAvatar avatar = new MemberAvatar("reports-avatar", "reports-avatar-label");
            avatar.setInitials(initials(m));
            loadPhoto(m.getId()).ifPresentOrElse(avatar::setPhoto, () -> avatar.setPhoto(null));

            Label name = new Label(Converter.capitalize(m.getFirstName()) + " " + Converter.capitalize(m.getLastName()));
            name.getStyleClass().add("reports-row-name");
            Label sub = new Label(m.getPlanName() == null ? "" : m.getPlanName());
            sub.getStyleClass().add("reports-row-sub");
            VBox identity = new VBox(1, name, sub);
            identity.setAlignment(Pos.CENTER_LEFT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Label ends = new Label(m.getEndDate() == null ? "" : m.getEndDate().format(dmy));
            ends.getStyleClass().add("reports-row-ends");
            Label pill = new Label(urgencyText(m, today));
            pill.getStyleClass().addAll("badge", urgencyClass(m, today));
            VBox rightCol = new VBox(3, ends, pill);
            rightCol.setAlignment(Pos.CENTER_RIGHT);

            HBox row = new HBox(11, avatar, identity, spacer, rightCol);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("reports-row");
            renewalsList.getChildren().add(row);
        }
        if (due.size() > shown) {
            Label more = new Label("+ " + (due.size() - shown) + " " + I18nService.get("more"));
            more.getStyleClass().add("reports-row-more");
            renewalsList.getChildren().add(more);
        }
    }

    /** Days until this membership lapses — whichever of end date / remaining visit-days comes first. */
    private static long daysLeft(Member m, LocalDate today) {
        long byDate = m.getEndDate() == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(today, m.getEndDate());
        return m.getRemainingDays() == null ? byDate : Math.min(byDate, m.getRemainingDays());
    }

    private String urgencyText(Member m, LocalDate today) {
        return Math.max(0, daysLeft(m, today)) + " " + I18nService.get("day_short");
    }

    private static String urgencyClass(Member m, LocalDate today) {
        long d = daysLeft(m, today);
        if (d <= 7) {
            return "badge-danger";
        }
        if (d <= 14) {
            return "badge-warning";
        }
        return "reports-badge-soon";
    }

    // ---- helpers ----

    private Label emptyLabel() {
        Label l = new Label(I18nService.get("No_data_for_period"));
        l.getStyleClass().add("reports-empty");
        return l;
    }

    private static String initials(Member m) {
        String f = m.getFirstName() == null || m.getFirstName().isBlank() ? "" : m.getFirstName().substring(0, 1);
        String l = m.getLastName() == null || m.getLastName().isBlank() ? "" : m.getLastName().substring(0, 1);
        return (f + l).toUpperCase();
    }

    private String money(long v) {
        return String.format(Locale.US, "%,d DZD", v);
    }

    private static String num(long v) {
        return String.format(Locale.US, "%,d", v);
    }
}
