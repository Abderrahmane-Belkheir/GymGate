package com.GymGate.UI.controller;

import com.GymGate.UI.component.MemberAvatar;
import com.GymGate.UI.component.TrendBarChart;
import com.GymGate.bussines.models.AttendanceRecord;
import com.GymGate.bussines.services.AppEvents;
import com.GymGate.bussines.services.AttendanceService;
import com.GymGate.bussines.services.I18nService;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
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

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.*;

import static com.GymGate.bussines.services.PhotosService.loadPhoto;

/**
 * Controller for the Attendance page (AttendanceView.fxml).
 * Lets the user pick a year/month/day, press Confirm to load that day's
 * check-in history, and renders it in a TableView styled to match the
 * Members table (identity group + right-aligned check-in group).
 */
public class AttendanceController {

    @FXML private ComboBox<Integer> yearCombo;
    @FXML private ComboBox<String> monthCombo;
    @FXML private ComboBox<Integer> dayCombo;
    @FXML private Button confirmButton;
    @FXML private Label dateHintLabel;

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
    @FXML private Label patternLabel;
    @FXML private Label statsHintLabel;
    @FXML private VBox totalBlock;
    @FXML private Region totalDivider;
    @FXML private Label totalLabel;

    @FXML private TableView<AttendanceRecord> attendanceTable;
    @FXML private TableColumn<AttendanceRecord, AttendanceRecord> memberColumn;
    @FXML private TableColumn<AttendanceRecord, AttendanceRecord> whenColumn;
    @FXML private Label countLabel;

    private final ObservableList<AttendanceRecord> tableItems = FXCollections.observableArrayList();

    private final AttendanceService attendanceService = AttendanceService.getInstance();

    /** A check-in arrived while this screen was off-view; re-run the query when
     *  it's shown again rather than on every background check-in. */
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

        // A check-in on the Home screen should land in this list immediately when
        // the selected date is today — but only bother if the screen is on view;
        // otherwise defer to the next time it's shown.
        AppEvents.subscribe(AppEvents.Type.ATTENDANCE_ADDED, () -> {
            if (attendanceTable.getScene() == null) {
                pendingReload = true;
            } else {
                onConfirm();
            }
        });

        attendanceTable.sceneProperty().addListener((obs, was, is) -> {
            if (is != null && pendingReload) {
                pendingReload = false;
                onConfirm();
            }
        });
    }
    private void updateCountLabel() {
        int count = tableItems.size();
        countLabel.setText(count + " " + I18nService.get(count == 1 ? "Check-in" : "Check-ins"));
    }
    private void setupTable() {
        attendanceTable.setItems(tableItems);
        attendanceTable.setPlaceholder(new Label(I18nService.get("No_check-ins_for_this_date.")));

        memberColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        memberColumn.setCellFactory(col -> new MemberCell());

        whenColumn.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        whenColumn.setCellFactory(col -> new WhenCell());
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

    /** Confirm button + ATTENDANCE_ADDED — refresh whichever view is showing. */
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

        List<AttendanceRecord> records = attendanceService.find(year, month, day);
        tableItems.setAll(records);
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
        dateHintLabel.setVisible(!stats);
        dateHintLabel.setManaged(!stats);

        countLabel.setVisible(!stats);
        countLabel.setManaged(!stats);
        totalBlock.setVisible(stats);
        totalBlock.setManaged(stats);
        totalDivider.setVisible(stats);
        totalDivider.setManaged(stats);

        updateDateSelectors();
        onConfirm();
    }

    /**
     * Shows only the date pickers the current view uses: list → year + month +
     * day; stats/month → year + month; stats/year → year; stats/all-time → none.
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
            Map<Integer, Integer> byMonth = attendanceService.countByMonth(year);
            for (int m = 1; m <= 12; m++) {
                cats.add(capitalize(Month.of(m).getDisplayName(TextStyle.SHORT, I18nService.getLocale())));
                vals.add(byMonth.getOrDefault(m, 0));
            }
            title = I18nService.get("Monthly_check-ins") + "  ·  " + year;
            currentIndex = year == today.getYear() ? today.getMonthValue() - 1 : -1;
            setShown(patternLabel, false);
            patternLabel.setText("");

        } else if (allTab.isSelected()) {
            SortedMap<Integer, Integer> byYear = attendanceService.countByYear();
            for (Map.Entry<Integer, Integer> e : byYear.entrySet()) {
                cats.add(String.valueOf(e.getKey()));
                vals.add(e.getValue());
            }
            title = I18nService.get("Yearly_check-ins");
            currentIndex = -1;
            setShown(patternLabel, false);
            patternLabel.setText("");

        } else {
            Map<Integer, Integer> byDay = attendanceService.countByDay(year, month);
            int len = YearMonth.of(year, month).lengthOfMonth();
            for (int d = 1; d <= len; d++) {
                cats.add(String.valueOf(d));
                vals.add(byDay.getOrDefault(d, 0));
            }
            title = I18nService.get("Daily_check-ins") + "  ·  "
                    + capitalize(Month.of(month).getDisplayName(TextStyle.FULL, I18nService.getLocale()))
                    + " " + year;
            currentIndex = year == today.getYear() && month == today.getMonthValue()
                    ? today.getDayOfMonth() - 1 : -1;

            BusiestPattern pattern = consistentBusiestPattern(year, month,
                    attendanceService.findTimestamps(year, month));
            if (pattern.day() != null && pattern.hour() != null) {
                String dayName = capitalize(pattern.day().getDisplayName(TextStyle.FULL, I18nService.getLocale()));
                patternLabel.setText(I18nService.get("Busiest_day") + " : " + dayName
                        + "   |   " + I18nService.get("Busiest_hour") + " : "
                        + String.format("%02d:00", pattern.hour()));
                setShown(patternLabel, true);
            } else {
                patternLabel.setText("");
                setShown(patternLabel, false);
            }
        }

        TrendBarChart.Stats stats = TrendBarChart.summarize(vals);
        statsTitleLabel.setText(title);
        statsHintLabel.setText(hint(stats));
        totalLabel.setText(String.format(Locale.US, "%,d", stats.total()));
        statsSlot.getChildren().setAll(TrendBarChart.build(
                cats, vals, stats, currentIndex, "", I18nService.get("No_check-in_data")));
    }

    /** "Average 12   ·   Peak 34" — the reference figures beside the title. */
    private static String hint(TrendBarChart.Stats s) {
        if (s.total() == 0) {
            return "";
        }
        return I18nService.get("Average") + " " + String.format(Locale.US, "%,d", Math.round(s.average()))
                + "     ·     " + I18nService.get("Peak") + " " + String.format(Locale.US, "%,d", s.peak());
    }

    /** The weekday and hour that are consistently busy, not just busy on their best day. */
    private record BusiestPattern(DayOfWeek day, Integer hour) { }

    /**
     * Finds the weekday and hour that stay busy week after week, rather than
     * whichever single day/hour happened to have the highest one-off total.
     *
     * <p>Weekday: every real occurrence of a weekday in the month (4 or 5 of
     * them) is one sample of that weekday's turnout; the weekday with the
     * highest <em>median</em> sample wins, so one unusually busy or dead week
     * can't crown (or bury) a day on its own.
     *
     * <p>Hour: the month is split into 7-day weeks and each week's total at
     * that hour is one sample, scored the same way — a median rather than a
     * single peak.
     */
    private static BusiestPattern consistentBusiestPattern(int year, int month, List<LocalDateTime> timestamps) {
        if (timestamps.isEmpty()) {
            return new BusiestPattern(null, null);
        }

        int len = YearMonth.of(year, month).lengthOfMonth();
        int[] perDate = new int[len];
        for (LocalDateTime t : timestamps) {
            perDate[t.getDayOfMonth() - 1]++;
        }

        Map<DayOfWeek, List<Integer>> byWeekday = new EnumMap<>(DayOfWeek.class);
        for (int d = 1; d <= len; d++) {
            DayOfWeek dow = LocalDate.of(year, month, d).getDayOfWeek();
            byWeekday.computeIfAbsent(dow, k -> new ArrayList<>()).add(perDate[d - 1]);
        }
        DayOfWeek bestDay = null;
        double bestDayMedian = -1;
        long bestDaySum = -1;
        for (Map.Entry<DayOfWeek, List<Integer>> e : byWeekday.entrySet()) {
            double med = median(e.getValue());
            long sum = e.getValue().stream().mapToLong(Integer::longValue).sum();
            if (med > bestDayMedian || (med == bestDayMedian && sum > bestDaySum)) {
                bestDayMedian = med;
                bestDaySum = sum;
                bestDay = e.getKey();
            }
        }

        Map<Integer, int[]> hourCountsByWeek = new TreeMap<>();
        for (LocalDateTime t : timestamps) {
            int week = (t.getDayOfMonth() - 1) / 7;
            hourCountsByWeek.computeIfAbsent(week, k -> new int[24])[t.getHour()]++;
        }
        Integer bestHour = null;
        double bestHourMedian = -1;
        long bestHourSum = -1;
        for (int h = 0; h < 24; h++) {
            List<Integer> perWeek = new ArrayList<>();
            long sum = 0;
            for (int[] counts : hourCountsByWeek.values()) {
                perWeek.add(counts[h]);
                sum += counts[h];
            }
            double med = median(perWeek);
            if (med > bestHourMedian || (med == bestHourMedian && sum > bestHourSum)) {
                bestHourMedian = med;
                bestHourSum = sum;
                bestHour = h;
            }
        }

        return new BusiestPattern(bestDayMedian > 0 ? bestDay : null, bestHourMedian > 0 ? bestHour : null);
    }

    private static double median(List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
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

    /**
     * IDENTITY group cell (memberColumn): avatar + member name, mirroring the
     * Members table's identity group — a trailing hairline divider (pinned to
     * the column's right edge by a growing spacer) marks the boundary with the
     * check-in group in the next column.
     */
    private static class MemberCell extends TableCell<AttendanceRecord, AttendanceRecord> {
        @Override
        protected void updateItem(AttendanceRecord record, boolean empty) {
            super.updateItem(record, empty);
            if (empty || record == null) {
                setGraphic(null);
                return;
            }

            MemberAvatar avatar = new MemberAvatar("attendance-avatar", "attendance-avatar-label");
            avatar.setInitials(initialsOf(record.getFullName()));
            loadPhoto(record.getId()).ifPresentOrElse(avatar::setPhoto, () -> avatar.setPhoto(null));

            Label name = new Label(record.getFullName());
            name.getStyleClass().add("member-row-name");

            VBox info = new VBox(name);
            info.setAlignment(Pos.CENTER_LEFT);

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            Region groupDivider = new Region();
            groupDivider.getStyleClass().add("member-column-divider");

            HBox content = new HBox(13, avatar, info, spacer, groupDivider);
            content.setAlignment(Pos.CENTER_LEFT);

            setGraphic(content);
        }
    }

    /**
     * CHECK-IN group cell (whenColumn): the check-in time as the primary
     * value, a thin subtle divider, then a restrained "Present" status pill —
     * same right-aligned value + divider + status structure the Members table
     * uses for its membership/status group.
     */
    private static class WhenCell extends TableCell<AttendanceRecord, AttendanceRecord> {
        WhenCell() {
            setAlignment(Pos.CENTER_RIGHT);
        }

        @Override
        protected void updateItem(AttendanceRecord record, boolean empty) {
            super.updateItem(record, empty);
            if (empty || record == null) {
                setGraphic(null);
                return;
            }

            FontIcon clockIcon = new FontIcon("fas-clock");
            clockIcon.getStyleClass().add("meta-icon");
            Label time = new Label(record.getFormattedTime());
            time.getStyleClass().add("attendance-when-time");
            HBox timeRow = new HBox(7, clockIcon, time);
            timeRow.setAlignment(Pos.CENTER_RIGHT);

            Region divider = new Region();
            divider.getStyleClass().add("member-status-divider");

            Label pill = new Label(I18nService.get("Present"));
            pill.getStyleClass().addAll("badge", "badge-active");

            HBox row = new HBox(16, timeRow, divider, pill);
            row.setAlignment(Pos.CENTER_RIGHT);
            setGraphic(row);
        }
    }
}