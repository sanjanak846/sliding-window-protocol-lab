package com.netsim.ui;

import com.netsim.experiment.*;
import com.netsim.sim.Protocol;
import com.netsim.sim.SimulationConfig;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.chart.LineChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

/** Experiment mode: parameter sweeps with repeated trials, run off the JavaFX thread. */
public final class ExperimentTab extends BorderPane {
    private final Supplier<SimulationConfig> configSupplier;
    private final TextField loss = new TextField("0, 5, 10, 20, 30"), corr = new TextField("0"),
            windows = new TextField("2, 4, 8, 16"), delays = new TextField("50"), packets = new TextField("100"),
            trials = new TextField("5"), baseSeed = new TextField("1"), timeoutFactor = new TextField("1.5");
    private final CheckBox gbn = new CheckBox("Go-Back-N"), sr = new CheckBox("Selective Repeat");
    private final Button run = new Button("Run experiment"), cancel = new Button("Cancel"), export = new Button("Export CSV");
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = new Label("Edit the value lists (comma separated) and run. Other parameters come from the configuration panel.");
    private final TableView<ExperimentRow> table = new TableView<>();
    private final LineChart<Number, Number> chart = ChartController.newLineChart();
    private final ChoiceBox<String> metricChoice = new ChoiceBox<>(FXCollections.observableArrayList(
            "Throughput (kbit/s)", "Delivery ratio (%)", "Retransmissions", "Avg delay (ms)", "Efficiency (%)", "Completion time (ms)"));
    private final ChoiceBox<String> xChoice = new ChoiceBox<>(FXCollections.observableArrayList(
            "Packet loss (%)", "Corruption (%)", "Window size", "Delay (ms)", "Packets"));
    private List<ExperimentRow> rows = List.of();
    private Task<List<ExperimentRow>> task;
    private AtomicBoolean cancelFlag;

    public ExperimentTab(Supplier<SimulationConfig> configSupplier) {
        this.configSupplier = configSupplier;
        gbn.setSelected(true);
        sr.setSelected(true);
        metricChoice.getSelectionModel().selectFirst();
        xChoice.getSelectionModel().selectFirst();

        GridPane g = new GridPane();
        g.setHgap(8); g.setVgap(6);
        g.addRow(0, new Label("Packet loss (%)"), loss, new Label("Corruption (%)"), corr, new Label("Window sizes"), windows);
        g.addRow(1, new Label("Delay (ms)"), delays, new Label("Packet counts"), packets, new Label("Trials per cell"), trials);
        g.addRow(2, new Label("Base seed"), baseSeed, new Label("Timeout = k x RTT (k, 0 = use panel)"), timeoutFactor, gbn, sr);
        run.getStyleClass().add("primary");
        HBox buttons = new HBox(8, run, cancel, export, progress, new Label("Chart:"), metricChoice, new Label("vs"), xChoice);
        buttons.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        VBox top = new VBox(8, Fx.heading("Experiment parameters"), g, buttons, status);
        top.setPadding(new Insets(10));
        setTop(top);

        table.getColumns().addAll(List.of(
                Fx.<ExperimentRow>col("Protocol", 110, r -> r.protocol().shortName()),
                Fx.<ExperimentRow>col("Loss %", 60, r -> Fx.num(r.lossPct(), 0)),
                Fx.<ExperimentRow>col("Corr %", 60, r -> Fx.num(r.corruptionPct(), 0)),
                Fx.<ExperimentRow>col("Window", 65, r -> String.valueOf(r.window())),
                Fx.<ExperimentRow>col("Delay ms", 70, r -> Fx.num(r.delayMs(), 0)),
                Fx.<ExperimentRow>col("Packets", 70, r -> String.valueOf(r.packets())),
                Fx.<ExperimentRow>col("Throughput kbit/s (mean \u00b1 sd)", 190, r -> pm(r.throughputKbps())),
                Fx.<ExperimentRow>col("Delivery % ", 110, r -> pm(r.deliveryRatioPct())),
                Fx.<ExperimentRow>col("Retransmissions", 130, r -> pm(r.retransmissions())),
                Fx.<ExperimentRow>col("Delay ms", 130, r -> pm(r.avgDelayMs())),
                Fx.<ExperimentRow>col("Efficiency %", 120, r -> pm(r.efficiencyPct())),
                Fx.<ExperimentRow>col("Invalid", 60, r -> String.valueOf(r.invalidRuns()))));
        SplitPane center = new SplitPane(chart, table);
        center.setOrientation(javafx.geometry.Orientation.VERTICAL);
        center.setDividerPositions(0.5);
        setCenter(center);

        cancel.setDisable(true);
        export.setDisable(true);
        run.setOnAction(e -> start());
        cancel.setOnAction(e -> { if (cancelFlag != null) cancelFlag.set(true); if (task != null) task.cancel(); });
        export.setOnAction(e -> export());
        metricChoice.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> redraw());
        xChoice.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> redraw());
    }

    private static String pm(ExperimentRow.Stat s) { return Fx.num(s.mean(), 2) + " \u00b1 " + Fx.num(s.std(), 2); }

    private void start() {
        SimulationConfig base = configSupplier.get();
        if (base == null) return;
        ExperimentSpec spec = new ExperimentSpec();
        List<String> errors = new ArrayList<>();
        spec.base = base.copy().recordEvents(false);
        spec.lossPct = doubles(loss, "Loss", errors);
        spec.corruptionPct = doubles(corr, "Corruption", errors);
        spec.windows = ints(windows, "Window sizes", errors);
        spec.delaysMs = doubles(delays, "Delay", errors);
        spec.packets = ints(packets, "Packet counts", errors);
        try {
            spec.trials = Integer.parseInt(trials.getText().trim());
            spec.baseSeed = Long.parseLong(baseSeed.getText().trim());
            spec.timeoutRttFactor = Double.parseDouble(timeoutFactor.getText().trim());
        } catch (NumberFormatException ex) { errors.add("Trials, seed and timeout factor must be numbers."); }
        List<Protocol> ps = new ArrayList<>();
        if (gbn.isSelected()) ps.add(Protocol.GO_BACK_N);
        if (sr.isSelected()) ps.add(Protocol.SELECTIVE_REPEAT);
        spec.protocols = ps;
        if (errors.isEmpty()) errors.addAll(spec.validate());
        if (!errors.isEmpty()) { Fx.error("Invalid experiment", String.join("\n", errors)); return; }

        final int cells = spec.combinations();
        cancelFlag = new AtomicBoolean(false);
        final AtomicBoolean flag = cancelFlag;
        task = new Task<>() {
            @Override protected List<ExperimentRow> call() throws Exception {
                return ExperimentRunner.run(spec, done -> updateProgress(done, cells), flag);
            }
        };
        progress.progressProperty().bind(task.progressProperty());
        run.setDisable(true); cancel.setDisable(false); export.setDisable(true);
        status.setText(String.format("Running %d simulations (%d cells x %d trials x %d protocols)...", spec.totalRuns(), cells, spec.trials, ps.size()));
        task.setOnSucceeded(e -> {
            rows = task.getValue();
            table.setItems(FXCollections.observableArrayList(rows));
            finish("Done: " + rows.size() + " result rows.");
            export.setDisable(rows.isEmpty());
            redraw();
        });
        task.setOnFailed(e -> { finish("Experiment failed."); Fx.error("Experiment failed", String.valueOf(task.getException())); });
        task.setOnCancelled(e -> finish("Cancelled."));
        Thread t = new Thread(task, "experiment-driver");
        t.setDaemon(true);
        t.start();
    }

    private void finish(String msg) {
        progress.progressProperty().unbind();
        progress.setProgress(0);
        run.setDisable(false);
        cancel.setDisable(true);
        status.setText(msg);
    }

    private void redraw() {
        if (rows.isEmpty()) return;
        String m = metricChoice.getValue(), x = xChoice.getValue();
        Function<ExperimentRow, ExperimentRow.Stat> metric = switch (m) {
            case "Delivery ratio (%)" -> ExperimentRow::deliveryRatioPct;
            case "Retransmissions" -> ExperimentRow::retransmissions;
            case "Avg delay (ms)" -> ExperimentRow::avgDelayMs;
            case "Efficiency (%)" -> ExperimentRow::efficiencyPct;
            case "Completion time (ms)" -> ExperimentRow::completionMs;
            default -> ExperimentRow::throughputKbps;
        };
        Function<ExperimentRow, Double> axis = switch (x) {
            case "Corruption (%)" -> ExperimentRow::corruptionPct;
            case "Window size" -> r -> (double) r.window();
            case "Delay (ms)" -> ExperimentRow::delayMs;
            case "Packets" -> r -> (double) r.packets();
            default -> ExperimentRow::lossPct;
        };
        ChartController.updateLine(chart, rows, x, axis, m, metric);
    }

    private void export() {
        FileChooser fc = new FileChooser();
        fc.setInitialFileName("experiment_results.csv");
        File f = fc.showSaveDialog(getScene().getWindow());
        if (f == null) return;
        try { CsvExporter.write(f.toPath(), CsvExporter.experimentToCsv(rows)); status.setText("Exported " + f.getName()); }
        catch (IOException ex) { Fx.error("Export failed", ex.getMessage()); }
    }

    private static List<Double> doubles(TextField f, String n, List<String> errors) {
        List<Double> out = new ArrayList<>();
        for (String s : f.getText().split(",")) {
            if (s.isBlank()) continue;
            try { out.add(Double.parseDouble(s.trim())); } catch (NumberFormatException e) { errors.add(n + ": '" + s.trim() + "' is not a number."); }
        }
        return out;
    }

    private static List<Integer> ints(TextField f, String n, List<String> errors) {
        List<Integer> out = new ArrayList<>();
        for (String s : f.getText().split(",")) {
            if (s.isBlank()) continue;
            try { out.add(Integer.parseInt(s.trim())); } catch (NumberFormatException e) { errors.add(n + ": '" + s.trim() + "' is not a whole number."); }
        }
        return out;
    }
}
