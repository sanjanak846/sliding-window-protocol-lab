package com.netsim.sim;

import java.util.PriorityQueue;

/** Priority queue of scheduled events ordered by (time, type priority, insertion order). */
public final class EventQueue {

    /** A scheduled event. Only the fields relevant to its type are populated. */
    public static final class Scheduled {
        final long time;
        final EventType type;
        final long order;
        Packet packet;
        Ack ack;
        int timerKey;
        long timerGen;
        long txEnd;

        Scheduled(long time, EventType type, long order) {
            this.time = time; this.type = type; this.order = order;
        }
        public long time() { return time; }
        public EventType type() { return type; }
    }

    private final PriorityQueue<Scheduled> pq = new PriorityQueue<>((a, b) -> {
        int c = Long.compare(a.time, b.time);
        if (c != 0) return c;
        c = Integer.compare(a.type.ordinal(), b.type.ordinal());
        if (c != 0) return c;
        return Long.compare(a.order, b.order);
    });
    private long counter = 0;

    public Scheduled add(long time, EventType type) {
        Scheduled s = new Scheduled(time, type, counter++);
        pq.add(s);
        return s;
    }

    public Scheduled poll() { return pq.poll(); }
    public boolean isEmpty() { return pq.isEmpty(); }
    public int size() { return pq.size(); }
}
