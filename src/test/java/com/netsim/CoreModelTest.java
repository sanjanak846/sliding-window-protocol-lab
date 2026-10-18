package com.netsim;

import com.netsim.sim.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CoreModelTest {

    @Test void configValidationAcceptsDefaults() {
        assertTrue(new SimulationConfig().validate().isEmpty());
    }

    @Test void configValidationRejectsBadValues() {
        assertFalse(new SimulationConfig().totalPackets(0).validate().isEmpty());
        assertFalse(new SimulationConfig().windowSize(0).validate().isEmpty());
        assertFalse(new SimulationConfig().lossProbability(-0.1).validate().isEmpty());
        assertFalse(new SimulationConfig().lossProbability(1.0).validate().isEmpty());
        assertFalse(new SimulationConfig().corruptionProbability(Double.NaN).validate().isEmpty());
        assertFalse(new SimulationConfig().propagationDelayMs(-1).validate().isEmpty());
        assertFalse(new SimulationConfig().dataRateMbps(0).validate().isEmpty());
        assertFalse(new SimulationConfig().timeoutMs(0).validate().isEmpty());
        assertFalse(new SimulationConfig().payloadBytes(0).validate().isEmpty());
        assertFalse(new SimulationConfig().lossProbability(0.95).corruptionProbability(0.95).validate().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> SimulationRunner.run(Protocol.GO_BACK_N, new SimulationConfig().totalPackets(-5)));
    }

    @Test void shortTimeoutProducesWarningNotError() {
        SimulationConfig c = new SimulationConfig().timeoutMs(10);
        assertTrue(c.validate().isEmpty());
        assertEquals(1, c.warnings().size());
    }

    @Test void derivedDelays() {
        SimulationConfig c = new SimulationConfig(); // 1000 B @ 1 Mbit/s, 50 ms prop, 1 ms ack
        assertEquals(8.0, c.transmissionDelayMs(), 1e-12);
        assertEquals(109.0, c.roundTripMs(), 1e-12);
    }

    @Test void sequenceSpaceRules() {
        assertEquals(5, Protocol.GO_BACK_N.sequenceSpace(4));
        assertEquals(8, Protocol.SELECTIVE_REPEAT.sequenceSpace(4));
        assertEquals(2, Protocol.SELECTIVE_REPEAT.sequenceSpace(1));
    }

    @Test void eventQueueOrdersByTimeThenTypeThenInsertion() {
        EventQueue q = new EventQueue();
        q.add(100, EventType.TIMER);
        q.add(50, EventType.TRANSMIT);
        q.add(100, EventType.ACK_ARRIVAL);
        q.add(100, EventType.DATA_ARRIVAL);
        EventQueue.Scheduled a = q.add(100, EventType.ACK_ARRIVAL); // later insertion, same time/type
        assertEquals(EventType.TRANSMIT, q.poll().type());
        assertEquals(EventType.DATA_ARRIVAL, q.poll().type());
        EventQueue.Scheduled firstAck = q.poll();
        assertEquals(EventType.ACK_ARRIVAL, firstAck.type());
        assertNotSame(a, firstAck);               // FIFO among equals
        assertSame(a, q.poll());
        assertEquals(EventType.TIMER, q.poll().type());
        assertTrue(q.isEmpty());
    }

    @Test void channelIsDeterministicAndZeroProbabilityIsClean() {
        Channel c1 = new Channel(99, 0.3, 0.2), c2 = new Channel(99, 0.3, 0.2);
        for (int id = 0; id < 200; id++) {
            assertEquals(c1.dataFate(id, 0), c2.dataFate(id, 0));
            assertEquals(c1.ackFate(id, 1), c2.ackFate(id, 1));
        }
        Channel clean = new Channel(5, 0, 0);
        for (int id = 0; id < 1000; id++) assertEquals(Fate.OK, clean.dataFate(id, 0));
    }

    @Test void channelLossAndCorruptionRatesMatchProbabilities() {
        Channel c = new Channel(1234, 0.20, 0.25);
        int n = 200_000, lost = 0, corrupt = 0;
        for (int i = 0; i < n; i++) {
            Fate f = c.dataFate(i, i % 7);
            if (f == Fate.LOST) lost++;
            if (f == Fate.CORRUPTED) corrupt++;
        }
        assertEquals(0.20, lost / (double) n, 0.01);
        // corruption applies to packets that were not lost: 0.8 * 0.25 = 0.20
        assertEquals(0.20, corrupt / (double) n, 0.01);
    }

    @Test void differentSeedsGiveDifferentFateSequences() {
        Channel a = new Channel(1, 0.5, 0), b = new Channel(2, 0.5, 0);
        int diff = 0;
        for (int i = 0; i < 200; i++) if (a.dataFate(i, 0) != b.dataFate(i, 0)) diff++;
        assertTrue(diff > 20);
    }

    @Test void sameTransmissionHasSameFateForBothProtocols() {
        // Common random numbers: the fate depends only on (seed, id, attempt), not on the protocol.
        SimulationConfig c = new SimulationConfig().totalPackets(30).windowSize(4).lossProbability(0.3).corruptionProbability(0.1).seed(3);
        SimulationResult g = SimulationRunner.run(Protocol.GO_BACK_N, c), s = SimulationRunner.run(Protocol.SELECTIVE_REPEAT, c);
        java.util.Map<String, Fate> gf = new java.util.HashMap<>();
        g.events().stream().filter(e -> e.kind() == SimulationEvent.Kind.TRANSMIT || e.kind() == SimulationEvent.Kind.RETRANSMIT)
                .forEach(e -> gf.put(e.packetId() + ":" + e.attempt(), e.fate()));
        s.events().stream().filter(e -> e.kind() == SimulationEvent.Kind.TRANSMIT || e.kind() == SimulationEvent.Kind.RETRANSMIT)
                .forEach(e -> { Fate other = gf.get(e.packetId() + ":" + e.attempt()); if (other != null) assertEquals(other, e.fate()); });
        assertFalse(gf.isEmpty());
    }
}
