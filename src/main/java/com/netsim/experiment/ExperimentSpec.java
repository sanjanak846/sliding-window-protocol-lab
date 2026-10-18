package com.netsim.experiment;

import com.netsim.sim.Protocol;
import com.netsim.sim.SimulationConfig;
import java.util.ArrayList;
import java.util.List;

/** Parameter sweep definition: the full cross product of the supplied value lists is simulated. */
public final class ExperimentSpec {
    public SimulationConfig base = new SimulationConfig().recordEvents(false);
    public List<Double> lossPct = List.of(0.0, 5.0, 10.0, 20.0, 30.0);
    public List<Double> corruptionPct = List.of(0.0);
    public List<Integer> windows = List.of(2, 4, 8, 16);
    public List<Double> delaysMs = List.of(50.0);
    public List<Integer> packets = List.of(100);
    public int trials = 5;
    public long baseSeed = 1;
    /** If > 0, timeout = factor * minimum RTT of each combination; otherwise base.timeoutMs is used. */
    public double timeoutRttFactor = 1.5;
    public List<Protocol> protocols = List.of(Protocol.GO_BACK_N, Protocol.SELECTIVE_REPEAT);
    public static final int MAX_RUNS = 200_000;

    public int combinations() {
        return lossPct.size() * corruptionPct.size() * windows.size() * delaysMs.size() * packets.size();
    }

    public long totalRuns() { return (long) combinations() * trials * protocols.size(); }

    public List<String> validate() {
        List<String> e = new ArrayList<>();
        if (lossPct.isEmpty() || corruptionPct.isEmpty() || windows.isEmpty() || delaysMs.isEmpty() || packets.isEmpty())
            e.add("Every parameter list needs at least one value.");
        if (protocols.isEmpty()) e.add("Select at least one protocol.");
        if (trials < 1 || trials > 1000) e.add("Trials must be between 1 and 1000.");
        if (!(timeoutRttFactor >= 0)) e.add("Timeout RTT factor must be >= 0.");
        if (e.isEmpty() && totalRuns() > MAX_RUNS) e.add("Too many runs (" + totalRuns() + "); limit is " + MAX_RUNS + ".");
        if (e.isEmpty()) {
            // validate the extreme combinations through the normal config validator
            for (double l : lossPct) for (double c : corruptionPct) for (int w : windows) for (double d : delaysMs) for (int p : packets) {
                e.addAll(configFor(l, c, w, d, p, 0).validate());
                if (!e.isEmpty()) return e.stream().distinct().toList();
            }
        }
        return e;
    }

    public SimulationConfig configFor(double lossPercent, double corrPercent, int window, double delayMs, int pkts, long seed) {
        SimulationConfig c = base.copy().lossProbability(lossPercent / 100.0).corruptionProbability(corrPercent / 100.0)
                .windowSize(window).propagationDelayMs(delayMs).totalPackets(pkts).seed(seed).recordEvents(false);
        if (timeoutRttFactor > 0) c.timeoutMs(timeoutRttFactor * c.roundTripMs());
        return c;
    }
}
