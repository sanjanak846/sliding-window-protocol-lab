package com.netsim.protocol;

import com.netsim.sim.*;
import com.netsim.sim.SimulationEvent.Kind;

/**
 * Selective Repeat.
 *
 * Sender: window W, sequence space M = 2W, one timer PER packet, individual ACKs. Only a timed-out,
 * unacknowledged packet is retransmitted. Receiver: window W starting at rcvBase; accepts and
 * buffers any packet inside the window, ACKs it, and delivers the contiguous prefix in order.
 * Packets from the previous window (already delivered) are re-ACKed so a lost ACK cannot stall the
 * sender; anything else is ignored. With M = 2W the offset (seq - rcvBase) mod M cleanly separates
 * "new window" (< W) from "previous window" (>= W).
 */
public final class SelectiveRepeatEngine implements ProtocolEngine {
    private final int total, window, space;
    // sender
    private int base = 0, next = 0;
    private final boolean[] acked;
    private final long[] timerGen;
    // receiver
    private int rcvBase = 0;
    private final Packet[] buffer;

    public SelectiveRepeatEngine(SimulationConfig cfg) {
        this.total = cfg.getTotalPackets();
        this.window = cfg.getWindowSize();
        this.space = Protocol.SELECTIVE_REPEAT.sequenceSpace(window);
        this.acked = new boolean[total];
        this.timerGen = new long[total];
        this.buffer = new Packet[space];
    }

    @Override public String name() { return Protocol.SELECTIVE_REPEAT.displayName(); }
    @Override public int sequenceSpace() { return space; }
    @Override public boolean isComplete() { return base >= total; }

    @Override public void start(NetworkSimulator sim) { fillWindow(sim); }

    private void transmit(int id, NetworkSimulator sim) {
        long end = sim.sendData(id, id % space);
        timerGen[id]++;
        sim.scheduleTimer(end + sim.timeoutMicros(), id, timerGen[id]);
    }

    private void fillWindow(NetworkSimulator sim) {
        while (next < total && next - base < window) transmit(next++, sim);
    }

    @Override public void onAckArrival(Ack ack, NetworkSimulator sim) {
        int id = -1;
        for (int k = base; k < next; k++) {
            if (k % space == ack.seqNum()) { id = k; break; }
        }
        if (id < 0 || acked[id]) {
            sim.stats().duplicateAcks++;
            sim.log(Kind.ACK_DUPLICATE, id < 0 ? ack.triggerId() : id, ack.seqNum(), "duplicate/old ACK(" + ack.seqNum() + ") ignored");
            return;
        }
        acked[id] = true;
        timerGen[id]++; // cancels the pending timer (it becomes stale)
        sim.log(Kind.ACK_RECEIVED, id, ack.seqNum(), "ACK(" + ack.seqNum() + ") acknowledges packet " + id);
        int oldBase = base;
        while (base < total && acked[base]) base++;
        if (base != oldBase) sim.log(Kind.WINDOW_SLIDE, base, base % space, "window slides: base=" + base + ", next=" + next);
        fillWindow(sim);
    }

    @Override public void onTimer(int id, long generation, NetworkSimulator sim) {
        if (generation != timerGen[id] || acked[id]) {
            sim.stats().staleTimers++;
            sim.log(Kind.STALE_TIMER, id, id % space, "stale timer for packet " + id + " ignored");
            return;
        }
        sim.stats().timeouts++;
        sim.log(Kind.TIMEOUT, id, id % space, "TIMEOUT on packet " + id + ": resend only this packet");
        transmit(id, sim);
    }

    @Override public void onDataArrival(Packet p, NetworkSimulator sim) {
        int offset = ((p.seqNum() - rcvBase) % space + space) % space;
        if (offset < window) {
            sim.sendAck(p.seqNum(), p);
            if (buffer[p.seqNum()] != null) {
                sim.stats().receiverDuplicates++;
                sim.log(Kind.DATA_DUPLICATE, p.id(), p.seqNum(), p.attempt(), 0, null, "DATA(seq=" + p.seqNum() + ") already buffered; re-ACKed");
                return;
            }
            buffer[p.seqNum()] = p;
            if (offset == 0) {
                sim.log(Kind.DATA_RECEIVED, p.id(), p.seqNum(), "DATA(seq=" + p.seqNum() + ") is the lowest expected packet");
            } else {
                sim.stats().buffered++;
                sim.log(Kind.DATA_BUFFERED, p.id(), p.seqNum(), p.attempt(), 0, null,
                        "DATA(seq=" + p.seqNum() + ") out of order, buffered (waiting for seq " + rcvBase + ")");
            }
            while (buffer[rcvBase] != null) {
                sim.deliver(buffer[rcvBase].id());
                buffer[rcvBase] = null;
                rcvBase = (rcvBase + 1) % space;
            }
        } else if (offset >= space - window) {
            sim.stats().receiverDuplicates++;
            sim.log(Kind.DATA_DUPLICATE, p.id(), p.seqNum(), p.attempt(), 0, null, "DATA(seq=" + p.seqNum() + ") duplicate of delivered packet; re-ACKed");
            sim.sendAck(p.seqNum(), p);
        } else {
            sim.log(Kind.DATA_REJECTED, p.id(), p.seqNum(), p.attempt(), 0, null, "DATA(seq=" + p.seqNum() + ") outside receiver window; ignored");
        }
    }
}
