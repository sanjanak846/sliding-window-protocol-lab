package com.netsim.sim;

import java.util.ArrayList;
import java.util.List;

/** Raw counters collected by the simulator. Turned into metrics by PerformanceAnalyzer. */
public final class RawStats {
    public String protocolName;
    public int totalPackets;
    public Status status = Status.COMPLETED;
    public String statusDetail = "";
    public long endMicros;
    public long transmissions;       // all data transmission attempts
    public long retransmissions;     // attempts beyond the first for each packet
    public long acksSent;
    public long lostData, corruptedData, lostAcks, corruptedAcks;
    public long timeouts, staleTimers, duplicateAcks;
    public long receiverDuplicates, outOfOrderRejected, buffered;
    public int delivered;
    public long eventsProcessed;
    public long[] firstTxMicros;     // -1 if never sent
    public long[] deliverMicros;     // -1 if never delivered
    public final List<SimulationEvent> events = new ArrayList<>();
    public boolean eventsTruncated;
}
