package com.netsim.sim;

import com.netsim.protocol.ProtocolEngine;
import com.netsim.sim.SimulationEvent.Kind;

/**
 * Discrete-event network environment shared by both protocols.
 *
 * One sender link (serialises transmissions: tx delay = payload/data rate), a one-way propagation delay
 * in each direction, and a faulty channel (see {@link Channel}). The clock is simulated time in
 * microseconds; nothing here sleeps or reads the wall clock.
 */
public final class NetworkSimulator {
    private final SimulationConfig cfg;
    private final Channel channel;
    private final EventQueue queue = new EventQueue();
    private final RawStats stats = new RawStats();
    private final SimulationListener listener;

    private final long txMicros, propMicros, ackDelayMicros, timeoutMicros;
    private final int[] attempts;
    private long clock = 0;
    private long linkFreeAt = 0;
    private boolean aborted = false;
    private ProtocolEngine engine;

    public NetworkSimulator(SimulationConfig cfg, SimulationListener listener) {
        this(cfg, listener, new Channel(cfg.getSeed(), cfg.getLossProbability(), cfg.getCorruptionProbability()));
    }

    /** Allows injecting a custom (e.g. scripted) channel, mainly for tests. */
    public NetworkSimulator(SimulationConfig cfg, SimulationListener listener, Channel channel) {
        this.cfg = cfg;
        this.listener = listener;
        this.channel = channel;
        this.txMicros = Math.max(1, SimulationConfig.toMicros(cfg.transmissionDelayMs()));
        this.propMicros = SimulationConfig.toMicros(cfg.getPropagationDelayMs());
        this.ackDelayMicros = SimulationConfig.toMicros(cfg.getAckDelayMs());
        this.timeoutMicros = Math.max(1, SimulationConfig.toMicros(cfg.getTimeoutMs()));
        this.attempts = new int[cfg.getTotalPackets()];
        stats.totalPackets = cfg.getTotalPackets();
        stats.firstTxMicros = new long[cfg.getTotalPackets()];
        stats.deliverMicros = new long[cfg.getTotalPackets()];
        java.util.Arrays.fill(stats.firstTxMicros, -1);
        java.util.Arrays.fill(stats.deliverMicros, -1);
    }

    // ----------------------------------------------------------------- engine-facing API

    public long now() { return clock; }
    public long timeoutMicros() { return timeoutMicros; }
    public SimulationConfig config() { return cfg; }
    public RawStats stats() { return stats; }
    public int attemptsMade(int id) { return attempts[id]; }

    /**
     * Queue a data packet on the sender link. Returns the simulated time at which its last bit leaves
     * the sender (the natural moment to start a retransmission timer).
     */
    public long sendData(int id, int seqNum) {
        int attempt = attempts[id];
        if (cfg.getMaxRetries() > 0 && attempt > cfg.getMaxRetries()) {
            abort(Status.ABORTED_RETRY_LIMIT, "packet " + id + " exceeded " + cfg.getMaxRetries() + " retransmissions");
            return clock;
        }
        attempts[id]++;
        long start = Math.max(clock, linkFreeAt);
        long end = start + txMicros;
        linkFreeAt = end;
        EventQueue.Scheduled ev = queue.add(start, EventType.TRANSMIT);
        ev.packet = new Packet(id, seqNum, attempt, null);
        ev.txEnd = end;
        return end;
    }

    /** Receiver emits an ACK in response to the given data packet. */
    public void sendAck(int ackSeq, Packet trigger) {
        stats.acksSent++;
        Fate fate = cfg.isAckErrorsEnabled() ? channel.ackFate(trigger.id(), trigger.attempt()) : Fate.OK;
        if (fate == Fate.LOST) stats.lostAcks++;
        if (fate == Fate.CORRUPTED) stats.corruptedAcks++;
        long arrive = clock + ackDelayMicros + propMicros;
        EventQueue.Scheduled ev = queue.add(arrive, EventType.ACK_ARRIVAL);
        ev.ack = new Ack(ackSeq, trigger.id(), trigger.attempt(), fate);
        log(Kind.ACK_SENT, trigger.id(), ackSeq, trigger.attempt(), arrive, fate,
                "ACK(" + ackSeq + ") sent" + (fate == Fate.OK ? "" : " [" + fate + " in channel]"));
    }

    public void scheduleTimer(long atMicros, int key, long generation) {
        EventQueue.Scheduled ev = queue.add(Math.max(atMicros, clock), EventType.TIMER);
        ev.timerKey = key;
        ev.timerGen = generation;
    }

    /** Receiver hands packet {@code id} to the application (in order). */
    public void deliver(int id) {
        if (stats.deliverMicros[id] >= 0) return; // never double-count
        stats.deliverMicros[id] = clock;
        stats.delivered++;
        log(Kind.DELIVER, id, -1, 0, 0, null, "packet " + id + " delivered to application");
    }

    public void log(Kind kind, int id, int seq, String detail) { log(kind, id, seq, 0, 0, null, detail); }

    public void log(Kind kind, int id, int seq, int attempt, long arrive, Fate fate, String detail) {
        SimulationEvent e = new SimulationEvent(clock, kind, id, seq, attempt, arrive, fate, detail);
        if (cfg.isRecordEvents()) {
            if (stats.events.size() < cfg.getMaxLoggedEvents()) stats.events.add(e);
            else stats.eventsTruncated = true;
        }
        if (listener != null) listener.onEvent(e);
    }

    public void abort(Status status, String detail) {
        if (aborted) return;
        aborted = true;
        stats.status = status;
        stats.statusDetail = detail;
        log(Kind.ABORT, -1, -1, detail);
    }

    // ----------------------------------------------------------------- main loop

    public RawStats run(ProtocolEngine eng) {
        this.engine = eng;
        stats.protocolName = eng.name();
        eng.start(this);
        while (!aborted && !eng.isComplete()) {
            EventQueue.Scheduled ev = queue.poll();
            if (ev == null) { abort(Status.STALLED, "event queue empty before completion"); break; }
            if (++stats.eventsProcessed > cfg.getMaxEvents()) {
                abort(Status.ABORTED_EVENT_LIMIT, "more than " + cfg.getMaxEvents() + " events");
                break;
            }
            clock = ev.time();
            dispatch(ev);
        }
        stats.endMicros = clock;
        if (!aborted) { stats.status = Status.COMPLETED; stats.statusDetail = Status.COMPLETED.description(); }
        return stats;
    }

    private void dispatch(EventQueue.Scheduled ev) {
        switch (ev.type()) {
            case TRANSMIT -> {
                Packet p = ev.packet;
                Fate fate = channel.dataFate(p.id(), p.attempt());
                p = p.withFate(fate);
                stats.transmissions++;
                if (p.attempt() > 0) stats.retransmissions++;
                if (stats.firstTxMicros[p.id()] < 0) stats.firstTxMicros[p.id()] = clock;
                if (fate == Fate.LOST) stats.lostData++;
                if (fate == Fate.CORRUPTED) stats.corruptedData++;
                long arrive = ev.txEnd + propMicros;
                EventQueue.Scheduled arr = queue.add(arrive, EventType.DATA_ARRIVAL);
                arr.packet = p;
                String what = (p.attempt() > 0 ? "retransmit" : "send") + " DATA(seq=" + p.seqNum() + ", pkt=" + p.id()
                        + ", try " + (p.attempt() + 1) + ")" + (fate == Fate.OK ? "" : " [" + fate + " in channel]");
                log(p.attempt() > 0 ? Kind.RETRANSMIT : Kind.TRANSMIT, p.id(), p.seqNum(), p.attempt(), arrive, fate, what);
            }
            case DATA_ARRIVAL -> {
                Packet p = ev.packet;
                if (p.fate() == Fate.LOST) {
                    log(Kind.DATA_LOST, p.id(), p.seqNum(), p.attempt(), 0, Fate.LOST, "DATA(seq=" + p.seqNum() + ") lost in channel");
                } else if (p.fate() == Fate.CORRUPTED) {
                    log(Kind.DATA_CORRUPTED, p.id(), p.seqNum(), p.attempt(), 0, Fate.CORRUPTED,
                            "DATA(seq=" + p.seqNum() + ") arrived corrupted; receiver discards it (checksum failure)");
                } else {
                    engine.onDataArrival(p, this);
                }
            }
            case ACK_ARRIVAL -> {
                Ack a = ev.ack;
                if (a.fate() == Fate.LOST) {
                    log(Kind.ACK_LOST, a.triggerId(), a.seqNum(), a.triggerAttempt(), 0, Fate.LOST, "ACK(" + a.seqNum() + ") lost in channel");
                } else if (a.fate() == Fate.CORRUPTED) {
                    log(Kind.ACK_CORRUPTED, a.triggerId(), a.seqNum(), a.triggerAttempt(), 0, Fate.CORRUPTED,
                            "ACK(" + a.seqNum() + ") arrived corrupted; sender discards it");
                } else {
                    engine.onAckArrival(a, this);
                }
            }
            case TIMER -> engine.onTimer(ev.timerKey, ev.timerGen, this);
        }
    }
}
