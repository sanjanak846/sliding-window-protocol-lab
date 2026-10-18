package com.netsim.ui;

import com.netsim.sim.SimulationResult;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import java.util.LinkedHashMap;
import java.util.Map;

/** Grid of metric cards for one simulation result. */
public final class MetricsPanel extends FlowPane {
    private final Map<String, Label> values = new LinkedHashMap<>();

    public MetricsPanel() {
        setHgap(8);
        setVgap(8);
        setPadding(new Insets(6));
        for (String t : new String[]{"Packets generated", "Unique delivered", "Delivery ratio (%)", "Transmission attempts",
                "Retransmissions", "ACKs sent", "Lost packets", "Corrupted packets", "Completion time (ms)",
                "Avg delivery delay (ms)", "Throughput (kbit/s)", "Efficiency (%)", "Channel utilization (%)", "Timeouts", "Status"}) {
            Label v = new Label("-");
            v.getStyleClass().add("metric-value");
            Label n = new Label(t);
            n.getStyleClass().add("metric-name");
            VBox card = new VBox(2, v, n);
            card.getStyleClass().add("metric-card");
            card.setPrefWidth(150);
            getChildren().add(card);
            values.put(t, v);
        }
    }

    public void clear() { values.values().forEach(l -> l.setText("-")); }

    public void show(SimulationResult r) {
        set("Packets generated", String.valueOf(r.totalPackets()));
        set("Unique delivered", String.valueOf(r.delivered()));
        set("Delivery ratio (%)", Fx.num(r.deliveryRatioPct(), 2));
        set("Transmission attempts", String.valueOf(r.transmissions()));
        set("Retransmissions", String.valueOf(r.retransmissions()));
        set("ACKs sent", String.valueOf(r.acks()));
        set("Lost packets", r.lostPackets() + " (+" + r.lostAcks() + " ACKs)");
        set("Corrupted packets", r.corruptedPackets() + " (+" + r.corruptedAcks() + " ACKs)");
        set("Completion time (ms)", Fx.num(r.completionTimeMs(), 2));
        set("Avg delivery delay (ms)", Fx.num(r.avgDelayMs(), 2));
        set("Throughput (kbit/s)", Fx.num(r.throughputKbps(), 2));
        set("Efficiency (%)", Fx.num(r.efficiencyPct(), 2));
        set("Channel utilization (%)", Fx.num(r.channelUtilizationPct(), 2));
        set("Timeouts", String.valueOf(r.timeouts()));
        set("Status", r.status().isValid() ? r.status().name().replace('_', ' ') : "INVALID: " + r.status().name().replace('_', ' '));
    }

    private void set(String k, String v) { values.get(k).setText(v); }
}
