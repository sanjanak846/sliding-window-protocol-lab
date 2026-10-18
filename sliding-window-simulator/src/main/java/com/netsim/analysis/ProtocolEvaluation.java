package com.netsim.analysis;

import com.netsim.sim.Protocol;
import com.netsim.sim.SimulationResult;
import java.util.List;

/** A protocol's simulation result, its constraint checks, eligibility and (informational) score. */
public record ProtocolEvaluation(Protocol protocol, SimulationResult result, List<ConstraintCheck> checks,
                                 boolean eligible, double score) {
    public List<ConstraintCheck> violations() { return checks.stream().filter(c -> !c.satisfied()).toList(); }
}
