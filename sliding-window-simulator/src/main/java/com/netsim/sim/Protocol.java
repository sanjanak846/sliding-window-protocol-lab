package com.netsim.sim;

import com.netsim.protocol.GoBackNEngine;
import com.netsim.protocol.ProtocolEngine;
import com.netsim.protocol.SelectiveRepeatEngine;

/** The two supported sliding-window protocols. */
public enum Protocol {
    GO_BACK_N("Go-Back-N", "GBN"),
    SELECTIVE_REPEAT("Selective Repeat", "SR");

    private final String displayName;
    private final String shortName;

    Protocol(String displayName, String shortName) {
        this.displayName = displayName;
        this.shortName = shortName;
    }

    public String displayName() { return displayName; }
    public String shortName() { return shortName; }

    /** Sequence-number space: GBN needs W+1, SR needs 2W to avoid ambiguity. */
    public int sequenceSpace(int windowSize) {
        return this == GO_BACK_N ? windowSize + 1 : 2 * windowSize;
    }

    public ProtocolEngine createEngine(SimulationConfig cfg) {
        return this == GO_BACK_N ? new GoBackNEngine(cfg) : new SelectiveRepeatEngine(cfg);
    }

    @Override public String toString() { return displayName; }
}
