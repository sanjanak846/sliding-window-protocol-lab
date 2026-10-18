package com.netsim.ui;

import com.netsim.sim.Fate;
import com.netsim.sim.Protocol;
import com.netsim.sim.SimulationEvent;
import com.netsim.sim.SimulationResult;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;

/**
 * Replays the event list produced by the simulation engine on a canvas (sender, channel lanes, receiver,
 * window strips). It is a pure VIEW: it reads SimulationEvents and never influences protocol logic.
 * Supports play / pause / resume / reset and single-step playback.
 */
public final class PacketVisualizer extends Pane {
    private record Flight(long start, long arrive, Fate fate, int id, int seq, boolean ack, boolean retransmit) { }

    private final Canvas canvas = new Canvas();
    private final Timeline timeline;
    private List<SimulationEvent> events = List.of();
    private int idx;
    private double simNow;            // simulated microseconds
    private long endMicros;
    private double speedMsPerSec = 150; // simulated milliseconds per real second
    private int total, window = 4, base, ackedUpTo;
    private boolean cumulative;
    private boolean[] sent = new boolean[0], acked = new boolean[0], delivered = new boolean[0], buffered = new boolean[0];
    private final List<Flight> flights = new ArrayList<>();
    private String banner = "Press Start to simulate and replay.";

    private Consumer<SimulationEvent> onEvent = e -> { };
    private DoubleConsumer onProgress = p -> { };
    private Runnable onFinished = () -> { };

    public PacketVisualizer() {
        setMinHeight(300);
        setPrefHeight(380);
        getChildren().add(canvas);
        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        widthProperty().addListener((o, a, b) -> draw());
        heightProperty().addListener((o, a, b) -> draw());
        timeline = new Timeline(new KeyFrame(Duration.millis(16), e -> tick(16)));
        timeline.setCycleCount(Animation.INDEFINITE);
        draw();
    }

    public void setOnEvent(Consumer<SimulationEvent> c) { onEvent = c; }
    public void setOnProgress(DoubleConsumer c) { onProgress = c; }
    public void setOnFinished(Runnable r) { onFinished = r; }
    public void setSpeed(double simMsPerRealSecond) { speedMsPerSec = Math.max(1, simMsPerRealSecond); }
    public boolean isPlaying() { return timeline.getStatus() == Animation.Status.RUNNING; }

    /** Prepare replay of a finished simulation (does not start playing). */
    public void load(SimulationResult r) {
        timeline.stop();
        events = r.events();
        total = r.totalPackets();
        window = r.config().getWindowSize();
        cumulative = r.protocol() == Protocol.GO_BACK_N;
        endMicros = events.isEmpty() ? 0 : events.get(events.size() - 1).timeMicros();
        reset();
    }

    public void play() { if (!events.isEmpty()) timeline.play(); }
    public void pause() { timeline.pause(); }
    public void resume() { if (!events.isEmpty() && idx < events.size()) timeline.play(); }

    public void reset() {
        timeline.stop();
        idx = 0; simNow = 0; base = 0; ackedUpTo = 0;
        sent = new boolean[total]; acked = new boolean[total]; delivered = new boolean[total]; buffered = new boolean[total];
        flights.clear();
        banner = events.isEmpty() ? "Press Start to simulate and replay." : "Ready. t = 0.000 ms";
        onProgress.accept(0);
        draw();
    }

    /** Step-by-step alternative to animation: apply exactly the next event. */
    public void step() {
        timeline.pause();
        if (idx >= events.size()) return;
        SimulationEvent e = events.get(idx++);
        simNow = Math.max(simNow, e.timeMicros());
        apply(e);
        finishFrame();
    }

    private void tick(double dtMs) {
        simNow += speedMsPerSec * dtMs; // (sim ms / real s) * real ms = sim microseconds
        while (idx < events.size() && events.get(idx).timeMicros() <= simNow) apply(events.get(idx++));
        finishFrame();
    }

    private void finishFrame() {
        flights.removeIf(f -> simNow > f.arrive + 150_000);
        onProgress.accept(endMicros <= 0 ? 1 : Math.min(1.0, simNow / endMicros));
        draw();
        if (idx >= events.size() && simNow >= endMicros && timeline.getStatus() == Animation.Status.RUNNING) {
            timeline.stop();
            onFinished.run();
        } else if (idx >= events.size() && timeline.getStatus() != Animation.Status.RUNNING) {
            onFinished.run();
        }
    }

    private void apply(SimulationEvent e) {
        int id = e.packetId();
        switch (e.kind()) {
            case TRANSMIT, RETRANSMIT -> {
                if (id >= 0 && id < total) sent[id] = true;
                flights.add(new Flight(e.timeMicros(), e.arriveMicros(), e.fate(), id, e.seqNum(), false, e.attempt() > 0));
            }
            case ACK_SENT -> flights.add(new Flight(e.timeMicros(), e.arriveMicros(), e.fate(), id, e.seqNum(), true, false));
            case DELIVER -> { if (id >= 0 && id < total) { delivered[id] = true; buffered[id] = false; } }
            case DATA_BUFFERED -> { if (id >= 0 && id < total) buffered[id] = true; }
            case ACK_RECEIVED -> {
                if (id >= 0 && id < total) {
                    if (cumulative) { for (int i = ackedUpTo; i <= id; i++) acked[i] = true; ackedUpTo = Math.max(ackedUpTo, id + 1); }
                    else acked[id] = true;
                }
            }
            case WINDOW_SLIDE -> base = id;
            default -> { }
        }
        banner = e.toString();
        onEvent.accept(e);
    }

    // ------------------------------------------------------------------ drawing

    private void draw() {
        double w = canvas.getWidth(), h = canvas.getHeight();
        if (w < 50 || h < 50) return;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.setFill(Color.web("#F8FAFC"));
        g.fillRect(0, 0, w, h);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);

        double stripsH = 120, chanH = h - stripsH - 30;
        double xs = 90, xr = w - 90;
        double laneData = 30 + chanH * 0.30, laneAck = 30 + chanH * 0.72;

        // banner
        g.setFill(Color.web("#334155"));
        g.setFont(Font.font("Monospaced", 11));
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText(clip(banner, (int) (w / 6.4)), 10, 12);
        g.setTextAlign(TextAlignment.CENTER);

        // endpoints
        endpoint(g, 10, laneData - 28, 70, laneAck - laneData + 56, "SENDER", "#1D4ED8");
        endpoint(g, w - 80, laneData - 28, 70, laneAck - laneData + 56, "RECEIVER", "#15803D");

        // lanes
        g.setStroke(Color.web("#CBD5E1"));
        g.setLineWidth(2);
        g.strokeLine(xs, laneData, xr, laneData);
        g.strokeLine(xs, laneAck, xr, laneAck);
        g.setFill(Color.web("#64748B"));
        g.setFont(Font.font("System", 10));
        g.fillText("DATA  \u2192", (xs + xr) / 2, laneData - 18);
        g.fillText("\u2190  ACK", (xs + xr) / 2, laneAck + 18);

        for (Flight f : flights) drawFlight(g, f, xs, xr, f.ack ? laneAck : laneData);

        drawStrips(g, w, h - stripsH);
    }

    private void endpoint(GraphicsContext g, double x, double y, double w, double h, String label, String color) {
        g.setFill(Color.web(color, 0.12));
        g.fillRoundRect(x, y, w, h, 12, 12);
        g.setStroke(Color.web(color));
        g.setLineWidth(1.5);
        g.strokeRoundRect(x, y, w, h, 12, 12);
        g.setFill(Color.web(color));
        g.setFont(Font.font("System", 11));
        g.fillText(label, x + w / 2, y + h / 2);
    }

    private void drawFlight(GraphicsContext g, Flight f, double xs, double xr, double y) {
        double span = Math.max(1, f.arrive - f.start);
        double frac = Math.max(0, Math.min(1, (simNow - f.start) / span));
        boolean lost = f.fate == Fate.LOST;
        double shown = lost ? Math.min(frac, 0.55) : frac;
        double x = f.ack ? xr - shown * (xr - xs) : xs + shown * (xr - xs);
        Color c = f.fate == Fate.LOST ? Color.web("#DC2626") : f.fate == Fate.CORRUPTED ? Color.web("#F97316")
                : f.ack ? Color.web("#10B981") : Color.web("#3B82F6");
        double bw = f.ack ? 30 : 34, bh = 18;
        g.setFill(lost && frac > 0.55 ? Color.web("#FECACA") : c);
        g.fillRoundRect(x - bw / 2, y - bh / 2, bw, bh, 6, 6);
        g.setStroke(f.retransmit ? Color.web("#111827") : c.darker());
        g.setLineWidth(f.retransmit ? 2.5 : 1);
        g.strokeRoundRect(x - bw / 2, y - bh / 2, bw, bh, 6, 6);
        g.setFill(Color.WHITE);
        g.setFont(Font.font("System", 9));
        g.fillText((f.ack ? "A" : "") + f.seq, x, y);
        if (lost && frac >= 0.55) {
            g.setFill(Color.web("#B91C1C"));
            g.setFont(Font.font("System", 16));
            g.fillText("\u2715", x, y - 16);
        } else if (f.fate == Fate.CORRUPTED && frac > 0.5) {
            g.setFill(Color.web("#C2410C"));
            g.setFont(Font.font("System", 14));
            g.fillText("!", x, y - 16);
        }
    }

    private void drawStrips(GraphicsContext g, double w, double top) {
        if (total == 0) return;
        double cell = 26, gap = 3, left = 90;
        int visible = (int) Math.max(1, (w - left - 20) / (cell + gap));
        int lo = Math.max(0, Math.min(base - 2, Math.max(0, total - visible)));
        int hi = Math.min(total, lo + visible);

        g.setFont(Font.font("System", 11));
        g.setFill(Color.web("#1D4ED8"));
        g.setTextAlign(TextAlignment.LEFT);
        g.fillText("Sender", 10, top + 30);
        g.setFill(Color.web("#15803D"));
        g.fillText("Receiver", 10, top + 76);
        g.setTextAlign(TextAlignment.CENTER);

        for (int i = lo; i < hi; i++) {
            double x = left + (i - lo) * (cell + gap);
            // sender strip
            Color sc = acked[i] ? Color.web("#34D399") : sent[i] ? Color.web("#FBBF24") : Color.web("#E2E8F0");
            g.setFill(sc);
            g.fillRoundRect(x, top + 18, cell, 24, 5, 5);
            // receiver strip
            Color rc = delivered[i] ? Color.web("#34D399") : buffered[i] ? Color.web("#A78BFA") : Color.web("#E2E8F0");
            g.setFill(rc);
            g.fillRoundRect(x, top + 64, cell, 24, 5, 5);
            g.setFill(Color.web("#0F172A"));
            g.setFont(Font.font("System", 9));
            g.fillText(String.valueOf(i), x + cell / 2, top + 30);
            g.fillText(String.valueOf(i), x + cell / 2, top + 76);
        }
        // sender window bracket
        int wl = Math.max(base, lo), wh = Math.min(base + window, hi);
        if (wh > wl) {
            double x0 = left + (wl - lo) * (cell + gap) - 2, x1 = left + (wh - lo) * (cell + gap) - gap + 2;
            g.setStroke(Color.web("#1D4ED8"));
            g.setLineWidth(2);
            g.strokeRoundRect(x0, top + 14, x1 - x0, 32, 8, 8);
        }
        // legend
        g.setFont(Font.font("System", 10));
        g.setTextAlign(TextAlignment.LEFT);
        double lx = left, ly = top + 106;
        lx = legend(g, lx, ly, "#E2E8F0", "not sent / not received");
        lx = legend(g, lx, ly, "#FBBF24", "sent, un-ACKed");
        lx = legend(g, lx, ly, "#34D399", "ACKed / delivered");
        legend(g, lx, ly, "#A78BFA", "buffered at receiver (SR)");
        g.setTextAlign(TextAlignment.CENTER);
    }

    private double legend(GraphicsContext g, double x, double y, String color, String text) {
        g.setFill(Color.web(color));
        g.fillRoundRect(x, y - 6, 12, 12, 3, 3);
        g.setFill(Color.web("#475569"));
        g.fillText(text, x + 16, y);
        return x + 16 + text.length() * 5.6 + 14;
    }

    private static String clip(String s, int n) { return s.length() <= n ? s : s.substring(0, Math.max(0, n - 1)) + "\u2026"; }
}
