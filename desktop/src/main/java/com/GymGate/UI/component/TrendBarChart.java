package com.GymGate.UI.component;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Line;
import javafx.scene.text.Text;
import javafx.util.Duration;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.Locale;

/**
 * The revenue / attendance trend chart shared by the Payments and Attendance
 * "Statistics" views. Flat bars, the peak column accented and a still-open
 * current period drawn lighter, solid hairline grid lines it paints itself, a
 * direct value labels on the peak and current bars, and a per-bar value
 * tooltip on hover.
 *
 * <p>It also clamps very wide bars so an "All-time" view with only a year or two
 * of data still reads as a bar chart rather than a couple of slabs, and it
 * disables Modena's own grid / zero lines (whose {@code em}-based dash arrays can
 * throw "dash lengths all zero" during an early layout pulse).
 *
 * <p>Style classes are shared: the node carries {@code payments-chart} and the
 * bars {@code payments-bar-strong} / {@code payments-bar-current}.
 */
public final class TrendBarChart extends BarChart<String, Number> {

    private static final double MAX_BAR_WIDTH = 64;

    private final Group gridLayer = new Group();
    private final Group overlayLayer = new Group();

    private int peakIndex = -1;
    private int currentIndex = -1;

    private TrendBarChart(CategoryAxis xAxis, NumberAxis yAxis) {
        super(xAxis, yAxis);

        setHorizontalGridLinesVisible(false);
        setVerticalGridLinesVisible(false);
        setHorizontalZeroLineVisible(false);
        setVerticalZeroLineVisible(false);
        setAlternativeRowFillVisible(false);
        setAlternativeColumnFillVisible(false);

        gridLayer.setManaged(false);
        gridLayer.setMouseTransparent(true);
        overlayLayer.setManaged(false);
        overlayLayer.setMouseTransparent(true);
        getPlotChildren().addAll(gridLayer, overlayLayer);
    }

    // ---- public API ----

    /** Total, peak, peak position and trailing-trimmed average of a series. */
    public record Stats(int total, int peak, int peakIndex, double average) { }

    public static Stats summarize(List<Integer> values) {
        int total = 0;
        int peak = 0;
        int peakIndex = -1;
        int lastActive = -1;
        for (int i = 0; i < values.size(); i++) {
            int v = values.get(i);
            total += v;
            if (v > peak) {
                peak = v;
                peakIndex = i;
            }
            if (v > 0) {
                lastActive = i;
            }
        }
        double average = 0;
        if (lastActive >= 0) {
            int windowSum = 0;
            for (int i = 0; i <= lastActive; i++) {
                windowSum += values.get(i);
            }
            average = (double) windowSum / (lastActive + 1);
        }
        return new Stats(total, peak, peakIndex, average);
    }

    /**
     * A fully-styled trend chart node, or an icon + {@code emptyText} placeholder
     * when there is no data. {@code currentIndex} marks a still-open period
     * (drawn lighter); pass {@code -1} for none. {@code unit} is shown after the
     * amount in the hover tooltip (e.g. "DZD"); pass {@code ""} for a bare count.
     */
    public static Region build(List<String> categories, List<Integer> values,
                               Stats stats, int currentIndex, String unit, String emptyText) {
        if (stats.total() == 0) {
            FontIcon icon = new FontIcon("fas-chart-bar");
            icon.getStyleClass().add("payments-stats-empty-icon");
            Label empty = new Label(emptyText);
            empty.getStyleClass().add("payments-stats-empty");
            VBox box = new VBox(12, icon, empty);
            box.setAlignment(Pos.CENTER);
            return new StackPane(box);
        }

        double[] scale = niceScale(stats.peak());

        CategoryAxis x = new CategoryAxis();
        x.setTickLabelRotation(0);
        x.setStartMargin(4);
        x.setEndMargin(4);
        NumberAxis y = new NumberAxis(0, scale[0], scale[1]);
        y.setMinorTickVisible(false);
        y.setTickMarkVisible(false);
        y.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number n) { return compact(n.intValue()); }
            @Override public Number fromString(String s) { return 0; }
        });

        TrendBarChart chart = new TrendBarChart(x, y);
        chart.getStyleClass().add("payments-chart");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.peakIndex = stats.peakIndex();
        chart.currentIndex = currentIndex;

        int n = categories.size();
        chart.setBarGap(1);
        chart.setCategoryGap(n >= 28 ? 10 : n >= 12 ? 18 : n >= 6 ? 30 : 46);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (int i = 0; i < n; i++) {
            series.getData().add(new XYChart.Data<>(categories.get(i), values.get(i)));
        }
        chart.getData().add(series);

        for (int i = 0; i < series.getData().size(); i++) {
            XYChart.Data<String, Number> data = series.getData().get(i);
            Node bar = data.getNode();
            if (bar == null) {
                continue;
            }
            if (i == currentIndex) {
                bar.getStyleClass().add("payments-bar-current");
            } else if (i == stats.peakIndex()) {
                bar.getStyleClass().add("payments-bar-strong");
            }
            Tooltip.install(bar, valueTooltip(data.getYValue().intValue(), unit));
        }

        chart.setOpacity(0);
        chart.setTranslateY(8);
        Platform.runLater(() -> {
            FadeTransition fade = new FadeTransition(Duration.millis(240), chart);
            fade.setToValue(1);
            TranslateTransition rise = new TranslateTransition(Duration.millis(260), chart);
            rise.setToY(0);
            rise.setInterpolator(Interpolator.EASE_OUT);
            new ParallelTransition(fade, rise).play();
        });

        return chart;
    }

    // ---- self-painted grid + bar clamp ----

    @Override
    protected void layoutPlotChildren() {
        super.layoutPlotChildren();

        clampBarWidths();

        NumberAxis yAxis = (NumberAxis) getYAxis();
        double plotWidth = getXAxis().getWidth();
        double tickUnit = yAxis.getTickUnit();
        double upper = yAxis.getUpperBound();

        gridLayer.getChildren().clear();
        if (tickUnit > 0 && plotWidth > 0) {
            for (double v = tickUnit; v <= upper + tickUnit * 0.001; v += tickUnit) {
                double gy = yAxis.getDisplayPosition(v);
                Line line = new Line(0, gy, plotWidth, gy);
                line.getStyleClass().add("payments-grid-line");
                gridLayer.getChildren().add(line);
            }
        }
        gridLayer.toBack();

        renderOverlay(plotWidth);
    }

    /**
     * The two direct value labels — peak in strong ink, the still-open current
     * period muted. Rebuilt every layout so it tracks bar geometry; purely
     * presentational, both figures come from the series the caller supplied.
     */
    private void renderOverlay(double plotWidth) {
        overlayLayer.getChildren().clear();
        if (plotWidth <= 0) {
            return;
        }

        if (!getData().isEmpty()) {
            List<XYChart.Data<String, Number>> points = getData().get(0).getData();
            addValueLabel(points, peakIndex, "payments-bar-value");
            if (currentIndex != peakIndex) {
                addValueLabel(points, currentIndex, "payments-bar-value-current");
            }
        }
        overlayLayer.toFront();
    }

    private void addValueLabel(List<XYChart.Data<String, Number>> points, int index, String styleClass) {
        if (index < 0 || index >= points.size()) {
            return;
        }
        Node bar = points.get(index).getNode();
        if (bar == null) {
            return;
        }
        Bounds b = bar.getBoundsInParent();
        if (b.getWidth() <= 0) {
            return;
        }
        Text label = new Text(compact(points.get(index).getYValue().intValue()));
        label.getStyleClass().add(styleClass);
        label.setTextOrigin(VPos.BASELINE);
        overlayLayer.getChildren().add(label);
        label.applyCss();
        double textWidth = label.getLayoutBounds().getWidth();
        label.setX(b.getMinX() + (b.getWidth() - textWidth) / 2.0);
        label.setY(Math.max(b.getMinY() - 6, 11));
    }

    private void clampBarWidths() {
        for (XYChart.Series<String, Number> series : getData()) {
            for (XYChart.Data<String, Number> item : series.getData()) {
                Node bar = item.getNode();
                if (bar == null) {
                    continue;
                }
                double width = bar.getLayoutBounds().getWidth();
                if (width > MAX_BAR_WIDTH) {
                    double centre = bar.getLayoutX() + width / 2.0;
                    bar.resizeRelocate(centre - MAX_BAR_WIDTH / 2.0, bar.getLayoutY(),
                            MAX_BAR_WIDTH, bar.getLayoutBounds().getHeight());
                }
            }
        }
    }

    // ---- helpers ----

    private static Tooltip valueTooltip(int amount, String unit) {
        Region dot = new Region();
        dot.getStyleClass().add("payments-bar-tip-dot");

        Label value = new Label(String.format(Locale.US, "%,d", amount));
        value.getStyleClass().add("payments-bar-tip-amount");

        HBox graphic = new HBox(6, dot, value);
        graphic.setAlignment(Pos.CENTER_LEFT);
        if (unit != null && !unit.isBlank()) {
            Label unitLabel = new Label(unit);
            unitLabel.getStyleClass().add("payments-bar-tip-currency");
            graphic.getChildren().add(unitLabel);
        }

        Tooltip tip = new Tooltip();
        tip.setGraphic(graphic);
        tip.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        tip.getStyleClass().add("payments-bar-tip");
        tip.setShowDelay(Duration.millis(90));
        tip.setHideDelay(Duration.millis(60));
        return tip;
    }

    /** Compact axis label: 12000 -> "12k", 1_500_000 -> "1.5M". Shared with {@link TrendStackedBarChart}. */
    static String compact(int v) {
        if (v >= 1_000_000) {
            return trimZero(v / 1_000_000.0) + "M";
        }
        if (v >= 1_000) {
            return trimZero(v / 1_000.0) + "k";
        }
        return String.valueOf(v);
    }

    private static String trimZero(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.format(Locale.US, "%.1f", d);
    }

    /**
     * A clean value axis: ~5 intervals on 1 / 2 / 2.5 / 5 / 10 × 10ⁿ steps.
     * Returns {upper, tickUnit}. The magnitude is floored at 1 so a small,
     * integer-only series (attendance counts) never gets fractional ticks.
     * Shared with {@link TrendStackedBarChart}.
     */
    static double[] niceScale(int max) {
        if (max <= 0) {
            return new double[]{10, 2};
        }
        double rough = max / 5.0;
        double mag = Math.max(1, Math.pow(10, Math.floor(Math.log10(rough))));
        double norm = rough / mag;
        double step = norm <= 1 ? 1 : norm <= 2 ? 2 : norm <= 2.5 ? 2.5 : norm <= 5 ? 5 : 10;
        double unit = step * mag;
        double upper = Math.ceil((max + unit * 0.15) / unit) * unit;
        return new double[]{upper, unit};
    }
}
