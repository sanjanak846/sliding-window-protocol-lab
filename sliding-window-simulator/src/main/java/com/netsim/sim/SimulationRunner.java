package com.netsim.sim;

import com.netsim.analysis.PerformanceAnalyzer;
import com.netsim.protocol.ProtocolEngine;

/** Convenience facade: validate config, run one protocol, compute metrics. */
public final class SimulationRunner {
    private SimulationRunner() { }

    public static SimulationResult run(Protocol protocol, SimulationConfig cfg) { return run(protocol, cfg, null); }

    public static SimulationResult run(Protocol protocol, SimulationConfig cfg, SimulationListener listener) {
        return run(protocol, cfg, listener, new Channel(cfg.getSeed(), cfg.getLossProbability(), cfg.getCorruptionProbability()));
    }

    public static SimulationResult run(Protocol protocol, SimulationConfig cfg, SimulationListener listener, Channel channel) {
        var errors = cfg.validate();
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join(" ", errors));
        ProtocolEngine engine = protocol.createEngine(cfg);
        NetworkSimulator sim = new NetworkSimulator(cfg, listener, channel);
        RawStats raw = sim.run(engine);
        return PerformanceAnalyzer.analyze(protocol, cfg, raw);
    }
}
