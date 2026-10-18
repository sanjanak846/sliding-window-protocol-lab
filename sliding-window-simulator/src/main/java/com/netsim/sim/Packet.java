package com.netsim.sim;

/**
 * A data packet transmission.
 * @param id      absolute packet index 0..N-1 (used only for metrics, never for protocol decisions)
 * @param seqNum  on-the-wire sequence number (id mod sequence space)
 * @param attempt 0 for the first transmission, 1.. for retransmissions
 * @param fate    channel fate, decided when the transmission starts
 */
public record Packet(int id, int seqNum, int attempt, Fate fate) {
    public Packet withFate(Fate f) { return new Packet(id, seqNum, attempt, f); }
}
