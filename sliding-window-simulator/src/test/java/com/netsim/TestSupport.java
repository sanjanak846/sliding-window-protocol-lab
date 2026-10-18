package com.netsim;

import com.netsim.sim.*;
import java.util.HashSet;
import java.util.Set;

/** Shared helpers: a scripted channel and config builders. */
final class TestSupport {
    private TestSupport() { }

    /** Channel whose fate is dictated by explicit (id,attempt) scripts; everything else is OK. */
    static final class ScriptedChannel extends Channel {
        final Set<String> lostData = new HashSet<>(), corruptData = new HashSet<>(), lostAck = new HashSet<>();
        final Set<Integer> alwaysLost = new HashSet<>();

        ScriptedChannel() { super(0, 0, 0); }

        ScriptedChannel loseData(int id, int attempt) { lostData.add(id + ":" + attempt); return this; }
        ScriptedChannel corruptData(int id, int attempt) { corruptData.add(id + ":" + attempt); return this; }
        ScriptedChannel loseAckFor(int id, int attempt) { lostAck.add(id + ":" + attempt); return this; }
        ScriptedChannel loseAllAttempts(int id) { alwaysLost.add(id); return this; }

        @Override public Fate dataFate(int id, int attempt) {
            if (alwaysLost.contains(id) || lostData.contains(id + ":" + attempt)) return Fate.LOST;
            if (corruptData.contains(id + ":" + attempt)) return Fate.CORRUPTED;
            return Fate.OK;
        }
        @Override public Fate ackFate(int id, int attempt) {
            return lostAck.contains(id + ":" + attempt) ? Fate.LOST : Fate.OK;
        }
    }

    /** Lossless defaults: tx 8 ms, prop 50 ms, ack 1 ms, timeout 150 ms, RTT 109 ms. */
    static SimulationConfig cfg(int packets, int window) {
        return new SimulationConfig().totalPackets(packets).windowSize(window)
                .lossProbability(0).corruptionProbability(0).seed(7);
    }

    static SimulationResult run(Protocol p, SimulationConfig c) { return SimulationRunner.run(p, c); }
    static SimulationResult run(Protocol p, SimulationConfig c, Channel ch) { return SimulationRunner.run(p, c, null, ch); }

    static long count(SimulationResult r, SimulationEvent.Kind k) {
        return r.events().stream().filter(e -> e.kind() == k).count();
    }
}
