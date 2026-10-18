package com.netsim.protocol;

import com.netsim.sim.Ack;
import com.netsim.sim.NetworkSimulator;
import com.netsim.sim.Packet;

/**
 * A sliding-window protocol (sender + receiver logic). Engines are driven exclusively by the
 * NetworkSimulator; they only see packets/ACKs that actually arrived intact (Fate.OK).
 */
public interface ProtocolEngine {
    String name();

    /** Called once at t=0 so the sender can fill its window. */
    void start(NetworkSimulator sim);

    /** Receiver side: an intact data packet arrived. */
    void onDataArrival(Packet packet, NetworkSimulator sim);

    /** Sender side: an intact ACK arrived. */
    void onAckArrival(Ack ack, NetworkSimulator sim);

    /** Timer expiry. Engines must ignore stale timers (generation mismatch). */
    void onTimer(int key, long generation, NetworkSimulator sim);

    /** True when the sender has all packets acknowledged. */
    boolean isComplete();

    int sequenceSpace();
}
