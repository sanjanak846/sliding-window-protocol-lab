package com.netsim.analysis;

import com.netsim.sim.Protocol;

/** Final decision plus the evidence behind it. {@code recommended} is null for TIE and NEITHER. */
public record Recommendation(Decision decision, Protocol recommended, ProtocolEvaluation gbn,
                             ProtocolEvaluation sr, String explanation) {
    public enum Decision { RECOMMEND_GBN, RECOMMEND_SR, TIE, NEITHER }
}
