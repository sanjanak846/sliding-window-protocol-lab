package com.netsim.sim;

import java.util.List;

/**
 * Metrics of one simulation run (see docs/METRICS.md for exact definitions).
 * avgDelayMs is NaN when no packet was delivered; throughput and efficiency are 0 in that case.
 */
public record SimulationResult(Protocol protocol, SimulationConfig config, Status status, String statusDetail,
                               int totalPackets, int delivered, double deliveryRatioPct,
                               long transmissions, long retransmissions, long acks,
                               long lostPackets, long corruptedPackets, long lostAcks, long corruptedAcks,
                               long timeouts, double completionTimeMs, double avgDelayMs,
                               double throughputBps, double efficiencyPct, double channelUtilizationPct,
                               long eventsProcessed, List<SimulationEvent> events, boolean eventsTruncated) {

    public double throughputKbps() { return throughputBps / 1000.0; }
    public boolean isValid() { return status.isValid(); }
}
