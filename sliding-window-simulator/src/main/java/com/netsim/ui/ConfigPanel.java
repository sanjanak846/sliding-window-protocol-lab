package com.netsim.ui;

import com.netsim.sim.SimulationConfig;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.ArrayList;
import java.util.List;

/** Shared network-configuration form. Probabilities are entered as percentages. */
public final class ConfigPanel extends VBox {
    private final TextField packets = field("50"), window = field("4"), loss = field("10"), corrupt = field("5"),
            prop = field("50"), payload = field("1000"), rate = field("1"), ackDelay = field("1"),
            timeout = field("150"), seed = field("42"), retries = field("50");
    private final CheckBox ackErrors = new CheckBox("Apply loss/corruption to ACKs too");
    private final Label messages = new Label();
    private final GridPane grid = new GridPane();
    private int row = 0;

    public ConfigPanel() {
        getStyleClass().add("config-panel");
        setSpacing(8);
        setPadding(new Insets(12));
        grid.setHgap(8);
        grid.setVgap(6);
        ColumnConstraints c0 = new ColumnConstraints(); c0.setPercentWidth(60);
        ColumnConstraints c1 = new ColumnConstraints(); c1.setPercentWidth(40);
        grid.getColumnConstraints().addAll(c0, c1);

        add("Total packets (count)", packets);
        add("Window size (packets)", window);
        add("Packet loss (%)", loss);
        add("Packet corruption (%)", corrupt);
        add("Propagation delay, one-way (ms)", prop);
        add("Payload size (bytes)", payload);
        add("Data rate (Mbit/s)", rate);
        add("ACK delay (ms)", ackDelay);
        add("Retransmission timeout (ms)", timeout);
        add("Random seed (integer)", seed);
        add("Max retransmissions / packet (0 = unlimited)", retries);

        Button autoTimeout = new Button("Set timeout = 1.5 x RTT");
        autoTimeout.setMaxWidth(Double.MAX_VALUE);
        autoTimeout.setOnAction(e -> {
            try {
                double tx = Double.parseDouble(payload.getText().trim()) * 8.0 / (Double.parseDouble(rate.getText().trim()) * 1000.0);
                double rtt = tx + 2 * Double.parseDouble(prop.getText().trim()) + Double.parseDouble(ackDelay.getText().trim());
                timeout.setText(String.format(java.util.Locale.ROOT, "%.1f", 1.5 * rtt));
            } catch (NumberFormatException ex) {
                messages.setText("Cannot compute RTT: check payload, data rate, delay and ACK delay.");
            }
        });
        ackErrors.setSelected(true);
        messages.setWrapText(true);
        messages.getStyleClass().add("hint");

        getChildren().addAll(Fx.heading("Network Configuration"),
                Fx.hint("Shared by every mode. Probabilities are percentages (0-99). Both protocols see the same channel luck for the same seed."),
                grid, ackErrors, autoTimeout, messages);
    }

    private static TextField field(String v) { TextField t = new TextField(v); t.setPrefColumnCount(6); return t; }

    private void add(String label, TextField f) {
        Label l = new Label(label);
        l.setWrapText(true);
        grid.add(l, 0, row);
        grid.add(f, 1, row);
        row++;
    }

    /** Parses and validates the form. Returns null (and shows the problems) when invalid. */
    public SimulationConfig read() {
        List<String> errors = new ArrayList<>();
        SimulationConfig c = new SimulationConfig()
                .totalPackets(i(packets, "Total packets", errors))
                .windowSize(i(window, "Window size", errors))
                .lossProbability(d(loss, "Packet loss", errors) / 100.0)
                .corruptionProbability(d(corrupt, "Packet corruption", errors) / 100.0)
                .propagationDelayMs(d(prop, "Propagation delay", errors))
                .payloadBytes(i(payload, "Payload size", errors))
                .dataRateMbps(d(rate, "Data rate", errors))
                .ackDelayMs(d(ackDelay, "ACK delay", errors))
                .timeoutMs(d(timeout, "Timeout", errors))
                .seed(l(seed, "Random seed", errors))
                .maxRetries(i(retries, "Max retransmissions", errors))
                .ackErrorsEnabled(ackErrors.isSelected());
        if (errors.isEmpty()) errors.addAll(c.validate());
        if (!errors.isEmpty()) {
            messages.setText(String.join("\n", errors));
            messages.setStyle("-fx-text-fill: #B91C1C;");
            Fx.error("Invalid configuration", String.join("\n", errors));
            return null;
        }
        messages.setStyle("");
        messages.setText(String.join("\n", c.warnings()));
        return c;
    }

    private static int i(TextField f, String n, List<String> e) {
        try { return Integer.parseInt(f.getText().trim()); }
        catch (NumberFormatException ex) { e.add(n + " must be a whole number."); return 0; }
    }

    private static long l(TextField f, String n, List<String> e) {
        try { return Long.parseLong(f.getText().trim()); }
        catch (NumberFormatException ex) { e.add(n + " must be a whole number."); return 0; }
    }

    private static double d(TextField f, String n, List<String> e) {
        try { return Double.parseDouble(f.getText().trim().replace(',', '.')); }
        catch (NumberFormatException ex) { e.add(n + " must be a number."); return Double.NaN; }
    }
}
