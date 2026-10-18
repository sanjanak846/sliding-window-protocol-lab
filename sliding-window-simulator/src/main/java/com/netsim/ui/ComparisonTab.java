package com.netsim.ui;

import com.netsim.analysis.PerformanceAnalyzer;
import com.netsim.experiment.CsvExporter;
import com.netsim.sim.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

/** Side-by-side comparison of both protocols under identical conditions. */
public final class ComparisonTab extends ScrollPane {
    private record Row(String metric, String gbn, String sr, String better) { }

    private final Supplier<SimulationConfig> configSupplier;
    private final TableView<Row> table = new TableView<>();
    private final FlowPane charts = new FlowPane(10, 10);
    private final TextArea explanation = new TextArea();
    private final Label status = new Label("Press \"Run comparison\" to simulate both protocols with the current configuration.");
    private List<SimulationResult> last;

    public ComparisonTab(Supplier<SimulationConfig> configSupplier) {
        this.configSupplier = configSupplier;
        Button run = new Button("Run comparison");
        run.getStyleClass().add("primary");
        Button export = new Button("Export CSV");
        run.setOnAction(e -> run());
        export.setOnAction(e -> export());

        table.getColumns().addAll(List.of(Fx.<Row>col("Metric", 220, Row::metric), Fx.<Row>col("Go-Back-N", 150, Row::gbn),
                Fx.<Row>col("Selective Repeat", 150, Row::sr), Fx.<Row>col("Better", 150, Row::better)));
        table.setPrefHeight(440);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        explanation.setEditable(false);
        explanation.setWrapText(true);
        explanation.setPrefRowCount(9);

        VBox box = new VBox(10, new HBox(8, run, export, status), Fx.heading("Metrics side by side"), table,
                Fx.heading("Charts"), charts, Fx.heading("Observed differences (computed from this run)"), explanation);
        box.setPadding(new Insets(12));
        setContent(box);
        setFitToWidth(true);
    }

    private void run() {
        SimulationConfig cfg = configSupplier.get();
        if (cfg == null) return;
        try {
            SimulationConfig c = cfg.copy().recordEvents(false);
            SimulationResult g = SimulationRunner.run(Protocol.GO_BACK_N, c), s = SimulationRunner.run(Protocol.SELECTIVE_REPEAT, c);
            last = List.of(g, s);
            fill(g, s);
            status.setText(String.format("Both runs used seed %d. GBN: %s | SR: %s", c.getSeed(), g.status(), s.status()));
        } catch (IllegalArgumentException ex) {
            Fx.error("Cannot simulate", ex.getMessage());
        }
    }

    private void fill(SimulationResult g, SimulationResult s) {
        table.setItems(FXCollections.observableArrayList(
                row("Packets generated", g.totalPackets(), s.totalPackets(), 0, 0, 0),
                row("Unique delivered", g.delivered(), s.delivered(), 0, 1, 1),
                row("Delivery ratio (%)", g.deliveryRatioPct(), s.deliveryRatioPct(), 2, 1, 1),
                row("Transmission attempts", g.transmissions(), s.transmissions(), 0, 0, -1),
                row("Retransmissions", g.retransmissions(), s.retransmissions(), 0, 0, -1),
                row("ACKs sent", g.acks(), s.acks(), 0, 0, -1),
                row("Lost data packets", g.lostPackets(), s.lostPackets(), 0, 0, 0),
                row("Corrupted data packets", g.corruptedPackets(), s.corruptedPackets(), 0, 0, 0),
                row("Timeouts", g.timeouts(), s.timeouts(), 0, 0, -1),
                row("Completion time (ms)", g.completionTimeMs(), s.completionTimeMs(), 2, 0, -1),
                row("Avg delivery delay (ms)", g.avgDelayMs(), s.avgDelayMs(), 2, 0, -1),
                row("Throughput (kbit/s)", g.throughputKbps(), s.throughputKbps(), 2, 0, 1),
                row("Protocol efficiency (%)", g.efficiencyPct(), s.efficiencyPct(), 2, 0, 1),
                row("Channel utilization (%)", g.channelUtilizationPct(), s.channelUtilizationPct(), 2, 0, 0)));
        charts.getChildren().setAll(
                ChartController.comparisonBar("Throughput", "kbit/s", g.throughputKbps(), s.throughputKbps()),
                ChartController.comparisonBar("Delivery ratio", "%", g.deliveryRatioPct(), s.deliveryRatioPct()),
                ChartController.comparisonBar("Retransmissions", "packets", g.retransmissions(), s.retransmissions()),
                ChartController.comparisonBar("Avg delay", "ms", g.avgDelayMs(), s.avgDelayMs()),
                ChartController.comparisonBar("Efficiency", "%", g.efficiencyPct(), s.efficiencyPct()));
        explanation.setText(PerformanceAnalyzer.explainComparison(g, s));
    }

    /** direction: +1 higher is better, -1 lower is better, 0 informational. */
    private static Row row(String name, double a, double b, int dec, int unusedDec, int direction) {
        String better = "-";
        if (direction != 0 && !Double.isNaN(a) && !Double.isNaN(b)) {
            if (Math.abs(a - b) < 1e-9) better = "tie";
            else better = ((a > b) == (direction > 0)) ? "Go-Back-N" : "Selective Repeat";
        }
        return new Row(name, Fx.num(a, dec), Fx.num(b, dec), better);
    }

    private void export() {
        if (last == null) return;
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("comparison.csv");
        File f = fc.showSaveDialog(getScene().getWindow());
        if (f == null) return;
        try { CsvExporter.write(f.toPath(), CsvExporter.resultsToCsv(last)); status.setText("Exported " + f.getName()); }
        catch (IOException ex) { Fx.error("Export failed", ex.getMessage()); }
    }
}
