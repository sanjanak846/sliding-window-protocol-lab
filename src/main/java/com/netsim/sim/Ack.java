package com.netsim.sim;

/**
 * An acknowledgement. Its fate is keyed by the data transmission that triggered it
 * (triggerId, triggerAttempt), which keeps comparisons between protocols fair.
 */
public record Ack(int seqNum, int triggerId, int triggerAttempt, Fate fate) { }
