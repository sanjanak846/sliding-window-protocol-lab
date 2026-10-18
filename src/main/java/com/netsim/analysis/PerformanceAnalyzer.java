package com.netsim.analysis;

import com.netsim.sim.*;

/** Converts raw simulation counters into the documented performance metrics. */
public final class PerformanceAnalyzer {
    private PerformanceAnalyzer() { }

    public static SimulationResult analyze(Protocol protocol, SimulationConfig cfg, RawStats raw) {
        int total = raw.totalPackets;
        double completionMs = raw.endMicros / 1000.0;
        double payloadBits = cfg.getPayloadBytes() * 8.0;

        double pdr = total > 0 ? raw.delivered * 100.0 / total : 0.0;

        double delaySumMs = 0;
        int n = 0;
        for (int i = 0; i < total; i++) {
            if (raw.deliverMicros[i] >= 0 && raw.firstTxMicros[i] >= 0) {
                delaySumMs += (raw.deliverMicros[i] - raw.firstTxMicros[i]) / 1000.0;
                n++;
            }
        }
        double avgDelay = n > 0 ? delaySumMs / n : Double.NaN;

        double throughputBps = completionMs > 0 ? raw.delivered * payloadBits / (completionMs / 1000.0) : 0.0;
        double efficiency = raw.transmissions > 0 ? raw.delivered * payloadBits * 100.0 / (raw.transmissions * payloadBits) : 0.0;
        double utilization = completionMs > 0
                ? Math.min(100.0, raw.transmissions * cfg.transmissionDelayMs() * 100.0 / completionMs) : 0.0;

        return new SimulationResult(protocol, cfg, raw.status, raw.statusDetail, total, raw.delivered, pdr,
                raw.transmissions, raw.retransmissions, raw.acksSent, raw.lostData, raw.corruptedData,
                raw.lostAcks, raw.corruptedAcks, raw.timeouts, completionMs, avgDelay, throughputBps,
                efficiency, utilization, raw.eventsProcessed, raw.events, raw.eventsTruncated);
    }

    /** Short factual comparison text built from measured values only. */
    public static String explainComparison(SimulationResult gbn, SimulationResult sr) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Under identical network conditions (seed %d, loss %.1f%%, corruption %.1f%%, window %d):%n",
                gbn.config().getSeed(), gbn.config().getLossProbability() * 100,
                gbn.config().getCorruptionProbability() * 100, gbn.config().getWindowSize()));
        if (!gbn.isValid() || !sr.isValid()) sb.append("WARNING: at least one run ended abnormally; its metrics are not reliable.\n");
        sb.append(cmp("Throughput", gbn.throughputKbps(), sr.throughputKbps(), "kbit/s", true));
        sb.append(cmp("Average delay", gbn.avgDelayMs(), sr.avgDelayMs(), "ms", false));
        sb.append(cmp("Retransmissions", gbn.retransmissions(), sr.retransmissions(), "", false));
        sb.append(cmp("Protocol efficiency", gbn.efficiencyPct(), sr.efficiencyPct(), "%", true));
        sb.append(cmp("Completion time", gbn.completionTimeMs(), sr.completionTimeMs(), "ms", false));
        if (gbn.retransmissions() > sr.retransmissions()) {
            sb.append(String.format("Go-Back-N resent %d more packets than Selective Repeat because every timeout/loss forces it to resend the whole outstanding window, including packets the receiver already had.%n",
                    gbn.retransmissions() - sr.retransmissions()));
        } else if (gbn.retransmissions() == sr.retransmissions()) {
            sb.append("Both protocols needed the same number of retransmissions here (typical when few or no errors occur).\n");
        } else {
            sb.append("Selective Repeat retransmitted more in this run (this can happen with premature timeouts or an unlucky error pattern); the result is not assumed to favour either protocol.\n");
        }
        return sb.toString();
    }

    private static String cmp(String name, double a, double b, String unit, boolean higherBetter) {
        if (Double.isNaN(a) || Double.isNaN(b)) return String.format("- %s: not available for at least one protocol.%n", name);
        String better;
        double tol = 1e-9 * Math.max(1, Math.max(Math.abs(a), Math.abs(b)));
        if (Math.abs(a - b) <= tol) better = "tie";
        else better = ((a > b) == higherBetter) ? "Go-Back-N better" : "Selective Repeat better";
        return String.format("- %s: GBN %.2f %s vs SR %.2f %s -> %s%n", name, a, unit, b, unit, better);
    }
}
