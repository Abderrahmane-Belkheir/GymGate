package com.GymGate.UI.component;

import javafx.animation.FadeTransition;
import javafx.animation.Interpolator;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.shape.Line;
import javafx.util.Duration;
import javafx.util.StringConverter;

import java.util.List;
import java.util.function.IntFunction;

/**
 * The two-series stacked companion to {@link TrendBarChart}, used for the
 * Reports "new vs renewal revenue" trend. Same visual treatment: solid hairline
 * grid lines it paints itself, quiet axes, a nice rounded value scale, and the
 * same fade-and-rise intro. Modena's own grid / zero lines are switched off
 * (their {@code em}-based dash arrays can throw during an early layout pulse).
 *
 * <p>The node carries {@code reports-trend-chart}; bar colours come from the
 * {@code .default-color0 / .default-color1 .chart-bar} rules in that block.
 */
public final class TrendStackedBarChart extends StackedBarChart<String, Number> {

    private final Group gridLayer = new Group();

    private TrendStackedBarChart(CategoryAxis xAxis, NumberAxis yAxis) {
        super(xAxis, yAxis);
        setHorizontalGridLinesVisible(false);
        setVerticalGridLinesVisible(false);
        setHorizontalZeroLineVisible(false);
        setVerticalZeroLineVisible(false);
        setAlternativeRowFillVisible(false);
        setAlternativeColumnFillVisible(false);
        gridLayer.setManaged(false);
        gridLayer.setMouseTransparent(true);
        getPlotChildren().add(gridLayer);
    }

    /**
     * Builds the chart. {@code rows} holds one {@code {bottom, top}} pair per
     * category (bottom series drawn first / lowest). {@code tip} turns a stack
     * value into tooltip text.
     */
    public static Region build(List<String> categories, List<int[]> rows,
                               String bottomName, String topName, IntFunction<String> tip) {
        int max = 0;
        for (int[] r : rows) {
            max = Math.max(max, r[0] + r[1]);
        }
        double[] scale = TrendBarChart.niceScale(max);

        CategoryAxis x = new CategoryAxis();
        x.setTickLabelRotation(0);
        x.setStartMargin(6);
        x.setEndMargin(6);
        NumberAxis y = new NumberAxis(0, scale[0], scale[1]);
        y.setMinorTickVisible(false);
        y.setTickMarkVisible(false);
        y.setTickLabelFormatter(new StringConverter<>() {
            @Override public String toString(Number n) { return TrendBarChart.compact(n.intValue()); }
            @Override public Number fromString(String s) { return 0; }
        });

        TrendStackedBarChart chart = new TrendStackedBarChart(x, y);
        chart.getStyleClass().add("reports-trend-chart");
        chart.setAnimated(false);
        chart.setLegendVisible(true);
        chart.setCategoryGap(categories.size() >= 12 ? 11 : 18);

        XYChart.Series<String, Number> bottom = new XYChart.Series<>();
        bottom.setName(bottomName);
        XYChart.Series<String, Number> top = new XYChart.Series<>();
        top.setName(topName);
        for (int i = 0; i < categories.size(); i++) {
            bottom.getData().add(new XYChart.Data<>(categories.get(i), rows.get(i)[0]));
            top.getData().add(new XYChart.Data<>(categories.get(i), rows.get(i)[1]));
        }
        chart.getData().add(bottom);
        chart.getData().add(top);

        for (XYChart.Series<String, Number> series : chart.getData()) {
            for (XYChart.Data<String, Number> d : series.getData()) {
                Node bar = d.getNode();
                if (bar != null) {
                    Tooltip.install(bar, new Tooltip(series.getName() + "   " + tip.apply(d.getYValue().intValue())));
                }
            }
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

    @Override
    protected void layoutPlotChildren() {
        super.layoutPlotChildren();

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
    }
}
