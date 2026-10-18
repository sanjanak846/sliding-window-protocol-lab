package com.netsim.sim;

/** One entry in the simulation log. The GUI replays these; it never feeds back into the protocol. */
public record SimulationEvent(long timeMicros, Kind kind, int packetId, int seqNum, int attempt,
                              long arriveMicros, Fate fate, String detail) {

    public enum Kind {
        TRANSMIT, RETRANSMIT, DATA_LOST, DATA_CORRUPTED, DATA_RECEIVED, DATA_BUFFERED,
        DATA_REJECTED, DATA_DUPLICATE, DELIVER, ACK_SENT, ACK_LOST, ACK_CORRUPTED,
        ACK_RECEIVED, ACK_DUPLICATE, TIMEOUT, STALE_TIMER, WINDOW_SLIDE, ABORT
    }

    public double timeMs() { return timeMicros / 1000.0; }

    @Override public String toString() {
        return String.format("[%10.3f ms] %-14s %s", timeMs(), kind, detail);
    }
}
