package com.netsim.protocol;

import com.netsim.sim.*;
import com.netsim.sim.SimulationEvent.Kind;

/**
 * Go-Back-N.
 *
 * Sender: window W, sequence space M = W+1, ONE timer covering the oldest unacknowledged packet
 * (base). ACKs are cumulative. On timeout, ALL outstanding packets base..next-1 are retransmitted.
 * Receiver: accepts only the packet with the expected sequence number; everything else is discarded
 * and answered with an ACK for the last in-order packet (nothing is sent if none was received yet).
 *
 * base/next are absolute indices on the sender; the wire only carries (index mod M). ACKs are
 * mapped back to an absolute index by searching the (at most W) outstanding packets, which is
 * unambiguous because M = W+1.
 */
public final class GoBackNEngine implements ProtocolEngine {
    private final int total, window, space;
    // sender
    private int base = 0, next = 0;
    private long timerGen = 0;
    private boolean timerRunning = false;
    // receiver
    private int expectedSeq = 0;
    private boolean receivedAny = false;

    public GoBackNEngine(SimulationConfig cfg) {
        this.total = cfg.getTotalPackets();
        this.window = cfg.getWindowSize();
        this.space = Protocol.GO_BACK_N.sequenceSpace(window);
    }

    @Override public String name() { return Protocol.GO_BACK_N.displayName(); }
    @Override public int sequenceSpace() { return space; }
    @Override public boolean isComplete() { return base >= total; }

    @Override public void start(NetworkSimulator sim) { fillWindow(sim); }

    private void fillWindow(NetworkSimulator sim) {
        while (next < total && next - base < window) {
            long end = sim.sendData(next, next % space);
            if (!timerRunning) startTimer(sim, end + sim.timeoutMicros());
            next++;
        }
    }

    private void startTimer(NetworkSimulator sim, long at) {
        timerGen++;
        timerRunning = true;
        sim.scheduleTimer(at, 0, timerGen);
    }

    private void stopTimer() { timerGen++; timerRunning = false; }

    @Override public void onAckArrival(Ack ack, NetworkSimulator sim) {
        int acked = -1;
        for (int k = base; k < next; k++) {
            if (k % space == ack.seqNum()) { acked = k; break; }
        }
        if (acked < 0) {
            sim.stats().duplicateAcks++;
            sim.log(Kind.ACK_DUPLICATE, ack.triggerId(), ack.seqNum(), "duplicate/old cumulative ACK(" + ack.seqNum() + ") ignored; base stays " + base);
            return;
        }
        sim.log(Kind.ACK_RECEIVED, acked, ack.seqNum(), "cumulative ACK(" + ack.seqNum() + ") confirms packets up to " + acked);
        base = acked + 1;
        sim.log(Kind.WINDOW_SLIDE, base, base % space, "window slides: base=" + base + ", next=" + next);
        if (base == next) stopTimer();
        else startTimer(sim, sim.now() + sim.timeoutMicros());
        fillWindow(sim);
    }

    @Override public void onTimer(int key, long generation, NetworkSimulator sim) {
        if (!timerRunning || generation != timerGen) {
            sim.stats().staleTimers++;
            sim.log(Kind.STALE_TIMER, -1, -1, "stale timer ignored");
            return;
        }
        sim.stats().timeouts++;
        sim.log(Kind.TIMEOUT, base, base % space, "TIMEOUT on base packet " + base + ": go back and resend " + base + ".." + (next - 1));
        long firstEnd = -1;
        for (int k = base; k < next; k++) {
            long end = sim.sendData(k, k % space);
            if (firstEnd < 0) firstEnd = end;
        }
        startTimer(sim, firstEnd + sim.timeoutMicros());
    }

    @Override public void onDataArrival(Packet p, NetworkSimulator sim) {
        if (p.seqNum() == expectedSeq) {
            sim.log(Kind.DATA_RECEIVED, p.id(), p.seqNum(), "DATA(seq=" + p.seqNum() + ") in order, accepted");
            sim.deliver(p.id());
            receivedAny = true;
            expectedSeq = (expectedSeq + 1) % space;
            sim.sendAck(p.seqNum(), p);
        } else {
            sim.stats().outOfOrderRejected++;
            sim.log(Kind.DATA_REJECTED, p.id(), p.seqNum(), p.attempt(), 0, null,
                    "DATA(seq=" + p.seqNum() + ") rejected: expected seq " + expectedSeq);
            if (receivedAny) sim.sendAck((expectedSeq - 1 + space) % space, p);
        }
    }
}
