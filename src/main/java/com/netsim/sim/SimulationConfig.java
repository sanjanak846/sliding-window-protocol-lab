package com.netsim.sim;

import java.util.ArrayList;
import java.util.List;

/**
 * All simulation parameters. Units: probabilities are FRACTIONS in [0,1] (the GUI shows percentages),
 * delays in milliseconds, data rate in Mbit/s, payload in bytes.
 */
public final class SimulationConfig {
    private int totalPackets = 50;
    private int windowSize = 4;
    private double lossProbability = 0.10;
    private double corruptionProbability = 0.05;
    private double propagationDelayMs = 50;   // one-way
    private int payloadBytes = 1000;
    private double dataRateMbps = 1.0;
    private double ackDelayMs = 1.0;          // ACK transmission/processing time
    private double timeoutMs = 150;
    private long seed = 42;
    private boolean ackErrorsEnabled = true;
    private int maxRetries = 50;              // retransmissions per packet; 0 = unlimited
    private long maxEvents = 2_000_000;
    private boolean recordEvents = true;
    private int maxLoggedEvents = 100_000;

    public int getTotalPackets() { return totalPackets; }
    public int getWindowSize() { return windowSize; }
    public double getLossProbability() { return lossProbability; }
    public double getCorruptionProbability() { return corruptionProbability; }
    public double getPropagationDelayMs() { return propagationDelayMs; }
    public int getPayloadBytes() { return payloadBytes; }
    public double getDataRateMbps() { return dataRateMbps; }
    public double getAckDelayMs() { return ackDelayMs; }
    public double getTimeoutMs() { return timeoutMs; }
    public long getSeed() { return seed; }
    public boolean isAckErrorsEnabled() { return ackErrorsEnabled; }
    public int getMaxRetries() { return maxRetries; }
    public long getMaxEvents() { return maxEvents; }
    public boolean isRecordEvents() { return recordEvents; }
    public int getMaxLoggedEvents() { return maxLoggedEvents; }

    public SimulationConfig totalPackets(int v) { totalPackets = v; return this; }
    public SimulationConfig windowSize(int v) { windowSize = v; return this; }
    public SimulationConfig lossProbability(double v) { lossProbability = v; return this; }
    public SimulationConfig corruptionProbability(double v) { corruptionProbability = v; return this; }
    public SimulationConfig propagationDelayMs(double v) { propagationDelayMs = v; return this; }
    public SimulationConfig payloadBytes(int v) { payloadBytes = v; return this; }
    public SimulationConfig dataRateMbps(double v) { dataRateMbps = v; return this; }
    public SimulationConfig ackDelayMs(double v) { ackDelayMs = v; return this; }
    public SimulationConfig timeoutMs(double v) { timeoutMs = v; return this; }
    public SimulationConfig seed(long v) { seed = v; return this; }
    public SimulationConfig ackErrorsEnabled(boolean v) { ackErrorsEnabled = v; return this; }
    public SimulationConfig maxRetries(int v) { maxRetries = v; return this; }
    public SimulationConfig maxEvents(long v) { maxEvents = v; return this; }
    public SimulationConfig recordEvents(boolean v) { recordEvents = v; return this; }
    public SimulationConfig maxLoggedEvents(int v) { maxLoggedEvents = v; return this; }

    public SimulationConfig copy() {
        return new SimulationConfig().totalPackets(totalPackets).windowSize(windowSize)
                .lossProbability(lossProbability).corruptionProbability(corruptionProbability)
                .propagationDelayMs(propagationDelayMs).payloadBytes(payloadBytes)
                .dataRateMbps(dataRateMbps).ackDelayMs(ackDelayMs).timeoutMs(timeoutMs).seed(seed)
                .ackErrorsEnabled(ackErrorsEnabled).maxRetries(maxRetries).maxEvents(maxEvents)
                .recordEvents(recordEvents).maxLoggedEvents(maxLoggedEvents);
    }

    /** Transmission delay of one data packet in ms: payload bits / data rate. */
    public double transmissionDelayMs() { return payloadBytes * 8.0 / (dataRateMbps * 1000.0); }

    /** Minimum possible round trip for one packet: tx + 2 * propagation + ACK delay (ms). */
    public double roundTripMs() { return transmissionDelayMs() + 2 * propagationDelayMs + ackDelayMs; }

    public static long toMicros(double ms) { return Math.round(ms * 1000.0); }

    /** @return human-readable validation errors; empty when valid. */
    public List<String> validate() {
        List<String> e = new ArrayList<>();
        if (totalPackets < 1 || totalPackets > 100_000) e.add("Total packets must be an integer between 1 and 100000.");
        if (windowSize < 1 || windowSize > 4096) e.add("Window size must be an integer between 1 and 4096.");
        if (!(lossProbability >= 0 && lossProbability <= 0.99)) e.add("Packet loss probability must be between 0% and 99%.");
        if (!(corruptionProbability >= 0 && corruptionProbability <= 0.99)) e.add("Corruption probability must be between 0% and 99%.");
        if (lossProbability + (1 - lossProbability) * corruptionProbability > 0.99)
            e.add("Combined loss+corruption probability must not exceed 99%.");
        if (!(propagationDelayMs >= 0 && propagationDelayMs <= 100_000)) e.add("Propagation delay must be between 0 and 100000 ms.");
        if (payloadBytes < 1 || payloadBytes > 65_535) e.add("Payload size must be between 1 and 65535 bytes.");
        if (!(dataRateMbps > 0 && dataRateMbps <= 1_000_000)) e.add("Data rate must be greater than 0 Mbit/s.");
        if (!(ackDelayMs >= 0 && ackDelayMs <= 100_000)) e.add("ACK delay must be between 0 and 100000 ms.");
        if (!(timeoutMs > 0 && timeoutMs <= 1_000_000)) e.add("Retransmission timeout must be greater than 0 ms.");
        if (maxRetries < 0) e.add("Max retries must be >= 0 (0 means unlimited).");
        if (maxEvents < 1000) e.add("Event limit must be at least 1000.");
        return e;
    }

    /** Non-fatal advisories (the configuration is still legal). */
    public List<String> warnings() {
        List<String> w = new ArrayList<>();
        if (timeoutMs < roundTripMs())
            w.add(String.format("Timeout (%.1f ms) is below the minimum RTT (%.1f ms): expect premature timeouts and spurious retransmissions.",
                    timeoutMs, roundTripMs()));
        return w;
    }
}
