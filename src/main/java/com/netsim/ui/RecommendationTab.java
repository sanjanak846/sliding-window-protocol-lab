package com.netsim.ui;

import com.netsim.analysis.*;
import com.netsim.sim.SimulationConfig;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Requirement-based protocol selection: hard constraints, soft priorities, decision with evidence. */
public final class RecommendationTab extends ScrollPane {
    private record Line(String protocol, ConstraintCheck check) { }

    private final Supplier<SimulationConfig> configSupplier;
    private final CheckBox pdrOn = new CheckBox("Min delivery ratio (%)"), delayOn = new CheckBox("Max avg delay (ms)"),
            thrOn = new CheckBox("Min throughput (kbit/s)"), effOn = new CheckBox("Min efficiency (%)"),
            retxOn = new CheckBox("Max retransmissions (count)");
    private final TextField pdr = new TextField("100"), delay = new TextField("500"), thr = new TextField("100"),
            eff = new TextField("30"), retx = new TextField("100");
    private final Slider wThr = slider(), wDelay = slider(), wRetx = slider(), wEff = slider();
    private final TextField tie = new TextField("0.02");
    private final Label decision = new Label("No recommendation yet");
    private final Label scores = new Label();
    private final TableView<Line> table = new TableView<>();
    private final TextArea explanation = new TextArea();

    public RecommendationTab(Supplier<SimulationConfig> configSupplier) {
        this.configSupplier = configSupplier;
        pdrOn.setSelected(true);
        delayOn.setSelected(true);
        thrOn.setSelected(true);

        GridPane hard = new GridPane();
        hard.setHgap(10); hard.setVgap(6);
        int r = 0;
        for (Object[] o : new Object[][]{{pdrOn, pdr}, {delayOn, delay}, {thrOn, thr}, {effOn, eff}, {retxOn, retx}}) {
            hard.add((CheckBox) o[0], 0, r);
            hard.add((TextField) o[1], 1, r++);
        }

        GridPane soft = new GridPane();
        soft.setHgap(10); soft.setVgap(6);
        soft.addRow(0, new Label("Throughput"), wThr);
        soft.addRow(1, new Label("Low delay"), wDelay);
        soft.addRow(2, new Label("Few retransmissions"), wRetx);
        soft.addRow(3, new Label("Efficiency"), wEff);
        soft.addRow(4, new Label("Tie margin (0-1)"), tie);

        VBox hardBox = new VBox(6, Fx.heading("Hard requirements (mandatory)"),
                Fx.hint("A protocol violating ANY ticked requirement is eliminated, whatever its score. The network conditions come from the shared configuration panel."), hard);
        VBox softBox = new VBox(6, Fx.heading("Priorities (only used if both protocols pass)"),
                Fx.hint("Weights 0-10. Score = weighted mean of per-metric scores in [0,1]: benefit metrics v/max(vGBN,vSR), cost metrics min(vGBN,vSR)/v."), soft);
        HBox forms = new HBox(30, hardBox, softBox);

        Button run = new Button("Simulate both and recommend");
        run.getStyleClass().add("primary");
        run.setOnAction(e -> run());

        decision.getStyleClass().add("decision");
        table.getColumns().addAll(List.of(Fx.<Line>col("Protocol", 130, l -> l.protocol()),
                Fx.<Line>col("Requirement", 170, l -> l.check().name() + " " + l.check().requirement()),
                Fx.<Line>col("Measured vs required", 330, l -> l.check().text()),
                Fx.<Line>col("Result", 80, l -> l.check().satisfied() ? "PASS" : "FAIL")));
        table.setPrefHeight(300);
        explanation.setEditable(false);
        explanation.setWrapText(true);
        explanation.setPrefRowCount(9);

        VBox box = new VBox(12, forms, run, decision, scores, Fx.heading("Constraint evaluation"), table,
                Fx.heading("Explanation (measured values)"), explanation);
        box.setPadding(new Insets(12));
        setContent(box);
        setFitToWidth(true);
    }

    private static Slider slider() {
        Slider s = new Slider(0, 10, 5);
        s.setShowTickLabels(true);
        s.setMajorTickUnit(5);
        s.setPrefWidth(220);
        return s;
    }

    private void run() {
        SimulationConfig cfg = configSupplier.get();
        if (cfg == null) return;
        List<String> errors = new ArrayList<>();
        Requirements q = new Requirements();
        q.minDeliveryRatioPct = pdrOn.isSelected() ? num(pdr, "Min delivery ratio", errors) : null;
        q.maxAvgDelayMs = delayOn.isSelected() ? num(delay, "Max avg delay", errors) : null;
        q.minThroughputKbps = thrOn.isSelected() ? num(thr, "Min throughput", errors) : null;
        q.minEfficiencyPct = effOn.isSelected() ? num(eff, "Min efficiency", errors) : null;
        q.maxRetransmissions = retxOn.isSelected() ? num(retx, "Max retransmissions", errors) : null;
        q.weightThroughput = wThr.getValue(); q.weightDelay = wDelay.getValue();
        q.weightRetransmissions = wRetx.getValue(); q.weightEfficiency = wEff.getValue();
        Double tm = num(tie, "Tie margin", errors);
        if (tm != null) q.tieMargin = tm;
        if (errors.isEmpty()) errors.addAll(q.validate());
        if (!errors.isEmpty()) { Fx.error("Invalid requirements", String.join("\n", errors)); return; }

        try {
            Recommendation rec = RecommendationEngine.evaluate(cfg, q);
            List<Line> lines = new ArrayList<>();
            rec.gbn().checks().forEach(c -> lines.add(new Line("Go-Back-N", c)));
            rec.sr().checks().forEach(c -> lines.add(new Line("Selective Repeat", c)));
            table.setItems(FXCollections.observableArrayList(lines));
            decision.setText(switch (rec.decision()) {
                case RECOMMEND_GBN -> "Recommended: Go-Back-N";
                case RECOMMEND_SR -> "Recommended: Selective Repeat";
                case TIE -> "Tie: no clear winner";
                case NEITHER -> "Neither protocol meets the requirements";
            });
            scores.setText(String.format("Weighted scores (informational unless both eligible):  GBN %.3f   |   SR %.3f   |   eligible: GBN %s, SR %s",
                    rec.gbn().score(), rec.sr().score(), rec.gbn().eligible() ? "yes" : "no", rec.sr().eligible() ? "yes" : "no"));
            explanation.setText(rec.explanation());
        } catch (IllegalArgumentException ex) {
            Fx.error("Cannot evaluate", ex.getMessage());
        }
    }

    private static Double num(TextField f, String name, List<String> errors) {
        try { return Double.parseDouble(f.getText().trim().replace(',', '.')); }
        catch (NumberFormatException e) { errors.add(name + " must be a number."); return null; }
    }
}
