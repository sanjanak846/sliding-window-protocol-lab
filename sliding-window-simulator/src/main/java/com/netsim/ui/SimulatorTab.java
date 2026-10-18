package com.netsim.ui;

import com.netsim.experiment.CsvExporter;
import com.netsim.sim.*;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

/** Individual simulation mode: run one protocol and replay it with live log, progress and metrics. */
public final class SimulatorTab extends BorderPane {
    private final Supplier<SimulationConfig> configSupplier;
    private final ChoiceBox<Protocol> protocol = new ChoiceBox<>(FXCollections.observableArrayList(Protocol.values()));
    private final Button start = new Button("Start"), pause = new Button("Pause"), resume = new Button("Resume"),
            step = new Button("Step"), reset = new Button("Reset"), export = new Button("Export CSV");
    private final Slider speed = new Slider(10, 2000, 150);
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = new Label("Idle");
    private final PacketVisualizer visualizer = new PacketVisualizer();
    private final ListView<String> log = new ListView<>();
    private final MetricsPanel metrics = new MetricsPanel();
    private SimulationResult last;

    public SimulatorTab(Supplier<SimulationConfig> configSupplier) {
        this.configSupplier = configSupplier;
        protocol.getSelectionModel().selectFirst();
        speed.setPrefWidth(160);

        HBox bar = new HBox(8, new Label("Protocol:"), protocol, start, pause, resume, step, reset, new Label("Speed:"), speed,
                progress, Fx.spacer(), export);
        bar.setPadding(new Insets(8));
        bar.getStyleClass().add("toolbar");
        bar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        start.getStyleClass().add("primary");

        SplitPane lower = new SplitPane(wrap("Event log", log), wrap("Performance metrics", new ScrollPane(metrics) {{ setFitToWidth(true); }}));
        lower.setDividerPositions(0.45);
        SplitPane main = new SplitPane(visualizer, lower);
        main.setOrientation(Orientation.VERTICAL);
        main.setDividerPositions(0.52);

        VBox top = new VBox(bar, status);
        status.setPadding(new Insets(0, 10, 6, 10));
        status.getStyleClass().add("hint");
        setTop(top);
        setCenter(main);

        visualizer.setOnEvent(e -> {
            log.getItems().add(e.toString());
            if (log.getItems().size() > 4000) log.getItems().remove(0, 1000);
            log.scrollTo(log.getItems().size() - 1);
        });
        visualizer.setOnProgress(progress::setProgress);
        visualizer.setOnFinished(() -> { status.setText(describe(last) + "  |  replay finished"); setButtons(false, false); });
        speed.valueProperty().addListener((o, a, b) -> visualizer.setSpeed(b.doubleValue()));
        visualizer.setSpeed(speed.getValue());

        start.setOnAction(e -> startRun());
        pause.setOnAction(e -> { visualizer.pause(); setButtons(true, true); });
        resume.setOnAction(e -> { visualizer.resume(); setButtons(true, false); });
        step.setOnAction(e -> { if (last != null) { visualizer.step(); setButtons(true, true); } });
        reset.setOnAction(e -> { visualizer.reset(); log.getItems().clear(); setButtons(false, false); status.setText(last == null ? "Idle" : describe(last)); });
        export.setOnAction(e -> exportCsv());
        setButtons(false, false);
    }

    private static VBox wrap(String title, javafx.scene.Node n) {
        Label l = Fx.heading(title);
        VBox.setVgrow(n, Priority.ALWAYS);
        VBox v = new VBox(4, l, n);
        v.setPadding(new Insets(6));
        return v;
    }

    private void startRun() {
        SimulationConfig cfg = configSupplier.get();
        if (cfg == null) return;
        try {
            last = SimulationRunner.run(protocol.getValue(), cfg);
        } catch (IllegalArgumentException ex) {
            Fx.error("Cannot simulate", ex.getMessage());
            return;
        }
        log.getItems().clear();
        metrics.show(last);
        visualizer.load(last);
        status.setText(describe(last) + (last.eventsTruncated() ? "  |  event log truncated at " + cfg.getMaxLoggedEvents() + " entries" : "")
                + "  |  replaying...");
        visualizer.play();
        setButtons(true, false);
    }

    private static String describe(SimulationResult r) {
        if (r == null) return "Idle";
        return r.protocol().displayName() + ": " + r.status().description() + String.format(" (simulated %.1f ms, %d events)", r.completionTimeMs(), r.eventsProcessed());
    }

    /** running: replay in progress/paused; paused: currently paused. */
    private void setButtons(boolean running, boolean paused) {
        pause.setDisable(!running || paused);
        resume.setDisable(!running || !paused);
        step.setDisable(last == null);
        reset.setDisable(last == null);
        export.setDisable(last == null);
    }

    private void exportCsv() {
        if (last == null) return;
        FileChooser fc = new FileChooser();
        fc.setTitle("Export event log (a *_metrics.csv file is written next to it)");
        fc.setInitialFileName("simulation_events.csv");
        File f = fc.showSaveDialog(getScene().getWindow());
        if (f == null) return;
        try {
            Path p = f.toPath();
            CsvExporter.write(p, CsvExporter.eventsToCsv(last.events()));
            String name = f.getName().replaceAll("\\.csv$", "") + "_metrics.csv";
            CsvExporter.write(p.resolveSibling(name), CsvExporter.resultsToCsv(List.of(last)));
            status.setText("Exported " + f.getName() + " and " + name);
        } catch (IOException ex) {
            Fx.error("Export failed", ex.getMessage());
        }
    }
}
