package com.netsim.experiment;

import com.netsim.sim.Protocol;

/** Aggregated result (mean and sample standard deviation over trials) for one parameter combination. */
public record ExperimentRow(Protocol protocol, double lossPct, double corruptionPct, int window, double delayMs,
                            int packets, int trials, int invalidRuns,
                            Stat throughputKbps, Stat deliveryRatioPct, Stat retransmissions, Stat avgDelayMs,
                            Stat efficiencyPct, Stat completionMs) {

    public record Stat(double mean, double std) {
        public static Stat of(double[] v, int n) {
            if (n == 0) return new Stat(Double.NaN, Double.NaN);
            double m = 0;
            for (int i = 0; i < n; i++) m += v[i];
            m /= n;
            double ss = 0;
            for (int i = 0; i < n; i++) ss += (v[i] - m) * (v[i] - m);
            return new Stat(m, n > 1 ? Math.sqrt(ss / (n - 1)) : 0.0);
        }
    }
}
