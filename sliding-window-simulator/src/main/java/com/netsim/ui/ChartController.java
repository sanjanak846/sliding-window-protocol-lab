package com.netsim.ui;

import com.netsim.experiment.ExperimentRow;
import com.netsim.sim.Protocol;
import com.netsim.sim.SimulationResult;
import javafx.scene.chart.*;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/** Factory/updater for all charts (kept free of simulation logic). */
public final class ChartController {
    private ChartController() { }

    /** One category, two bars (GBN vs SR) for a single metric. */
    public static BarChart<String, Number> comparisonBar(String title, String unit, double gbn, double sr) {
        CategoryAxis x = new CategoryAxis();
        NumberAxis y = new NumberAxis();
        y.setLabel(unit);
        BarChart<String, Number> chart = new BarChart<>(x, y);
        chart.setTitle(title);
        chart.setAnimated(false);
        chart.setPrefSize(330, 250);
        chart.setCategoryGap(20);
        XYChart.Series<String, Number> a = new XYChart.Series<>();
        a.setName("Go-Back-N");
        a.getData().add(new XYChart.Data<>(title, Double.isNaN(gbn) ? 0 : gbn));
        XYChart.Series<String, Number> b = new XYChart.Series<>();
        b.setName("Selective Repeat");
        b.getData().add(new XYChart.Data<>(title, Double.isNaN(sr) ? 0 : sr));
        chart.getData().addAll(List.of(a, b));
        return chart;
    }

    public static LineChart<Number, Number> newLineChart() {
        NumberAxis x = new NumberAxis();
        x.setForceZeroInRange(false);
        NumberAxis y = new NumberAxis();
        y.setForceZeroInRange(false);
        LineChart<Number, Number> c = new LineChart<>(x, y);
        c.setAnimated(false);
        c.setCreateSymbols(true);
        return c;
    }

    /** Plots the mean of {@code metric} against {@code xAxis}, averaging over all other parameters. */
    public static void updateLine(LineChart<Number, Number> chart, List<ExperimentRow> rows, String xLabel,
                                  Function<ExperimentRow, Double> xAxis, String yLabel,
                                  Function<ExperimentRow, ExperimentRow.Stat> metric) {
        chart.getData().clear();
        ((NumberAxis) chart.getXAxis()).setLabel(xLabel);
        ((NumberAxis) chart.getYAxis()).setLabel(yLabel);
        chart.setTitle(yLabel + " vs " + xLabel);
        for (Protocol p : Protocol.values()) {
            Map<Double, double[]> acc = new TreeMap<>();
            for (ExperimentRow r : rows) {
                if (r.protocol() != p) continue;
                double v = metric.apply(r).mean();
                if (Double.isNaN(v)) continue;
                double[] s = acc.computeIfAbsent(xAxis.apply(r), k -> new double[2]);
                s[0] += v;
                s[1] += 1;
            }
            if (acc.isEmpty()) continue;
            XYChart.Series<Number, Number> series = new XYChart.Series<>();
            series.setName(p.displayName());
            acc.forEach((x, s) -> series.getData().add(new XYChart.Data<>(x, s[0] / s[1])));
            chart.getData().add(series);
        }
    }

    public static double value(SimulationResult r, String which) {
        return switch (which) {
            case "throughput" -> r.throughputKbps();
            case "pdr" -> r.deliveryRatioPct();
            case "retx" -> r.retransmissions();
            case "delay" -> r.avgDelayMs();
            case "eff" -> r.efficiencyPct();
            default -> Double.NaN;
        };
    }
}
