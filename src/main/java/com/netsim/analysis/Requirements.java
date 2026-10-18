package com.netsim.analysis;

import java.util.ArrayList;
import java.util.List;

/**
 * User requirements. A null threshold means "not specified". Non-null thresholds are HARD constraints;
 * the weights/tieMargin only matter among protocols that satisfy every hard constraint.
 */
public final class Requirements {
    public Double minDeliveryRatioPct = 100.0;   // hard: delivered/generated * 100 >= value (100 - this = max residual loss %)
    public Double maxAvgDelayMs = null;          // hard
    public Double minThroughputKbps = null;      // hard
    public Double minEfficiencyPct = null;       // hard
    public Double maxRetransmissions = null;     // hard (absolute count)

    public double weightThroughput = 1.0;        // soft preferences (>= 0)
    public double weightDelay = 1.0;
    public double weightRetransmissions = 1.0;
    public double weightEfficiency = 1.0;
    public double tieMargin = 0.02;              // |scoreA - scoreB| below this => tie (0..1)

    public List<String> validate() {
        List<String> e = new ArrayList<>();
        if (minDeliveryRatioPct != null && !(minDeliveryRatioPct >= 0 && minDeliveryRatioPct <= 100))
            e.add("Minimum delivery ratio must be between 0 and 100 %.");
        if (maxAvgDelayMs != null && !(maxAvgDelayMs > 0)) e.add("Maximum average delay must be greater than 0 ms.");
        if (minThroughputKbps != null && !(minThroughputKbps >= 0)) e.add("Minimum throughput must be >= 0 kbit/s.");
        if (minEfficiencyPct != null && !(minEfficiencyPct >= 0 && minEfficiencyPct <= 100))
            e.add("Minimum efficiency must be between 0 and 100 %.");
        if (maxRetransmissions != null && !(maxRetransmissions >= 0)) e.add("Maximum retransmissions must be >= 0.");
        if (!(weightThroughput >= 0) || !(weightDelay >= 0) || !(weightRetransmissions >= 0) || !(weightEfficiency >= 0))
            e.add("Priority weights must be >= 0.");
        else if (weightThroughput + weightDelay + weightRetransmissions + weightEfficiency <= 0)
            e.add("At least one priority weight must be greater than 0.");
        if (!(tieMargin >= 0 && tieMargin <= 1)) e.add("Tie margin must be between 0 and 1.");
        return e;
    }
}
