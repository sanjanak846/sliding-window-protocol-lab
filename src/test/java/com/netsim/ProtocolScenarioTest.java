package com.netsim;

import com.netsim.sim.*;
import com.netsim.sim.SimulationEvent.Kind;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static com.netsim.TestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/** Scenario tests that exercise BOTH protocols through their own engines. */
class ProtocolScenarioTest {

    // ------------------------------------------------------------ scenario 1 & 9: single packet
    @Test void singlePacketNoErrors() {
        for (Protocol p : Protocol.values()) {
            SimulationResult r = run(p, cfg(1, 4));
            assertEquals(Status.COMPLETED, r.status());
            assertEquals(1, r.delivered());
            assertEquals(1, r.transmissions());
            assertEquals(0, r.retransmissions());
            assertEquals(1, r.acks());
            assertEquals(109.0, r.completionTimeMs(), 1e-9);   // 8 tx + 50 prop + 1 ack + 50 prop
            assertEquals(58.0, r.avgDelayMs(), 1e-9);          // 8 tx + 50 prop
            assertEquals(100.0, r.deliveryRatioPct(), 1e-9);
            assertEquals(100.0, r.efficiencyPct(), 1e-9);
        }
    }

    @Test void singlePacketWithFirstAttemptLost() {
        for (Protocol p : Protocol.values()) {
            SimulationResult r = run(p, cfg(1, 1), new ScriptedChannel().loseData(0, 0));
            assertEquals(1, r.retransmissions());
            assertEquals(1, r.timeouts());
            assertEquals(1, r.lostPackets());
            // timer at 8+150=158 -> resend 158..166 -> arrives 216 -> ACK at 267
            assertEquals(267.0, r.completionTimeMs(), 1e-9);
            assertEquals(216.0, r.avgDelayMs(), 1e-9);          // delay includes the wait for the timeout
            assertEquals(50.0, r.efficiencyPct(), 1e-9);        // 1 delivered / 2 transmitted
        }
    }

    // ------------------------------------------------------------ scenario 2: no loss, many packets
    @Test void multiplePacketsNoLossDeliveredInOrderWithoutRetransmission() {
        for (Protocol p : Protocol.values()) {
            SimulationResult r = run(p, cfg(20, 4));
            assertEquals(20, r.delivered());
            assertEquals(0, r.retransmissions());
            assertEquals(20, r.transmissions());
            assertEquals(20, r.acks());
            assertEquals(0, r.timeouts());
            assertEquals(569.0, r.completionTimeMs(), 1e-9);
            assertEquals(58.0, r.avgDelayMs(), 1e-9);
        }
    }

    @Test void everyPacketIsGeneratedWithCorrectSequenceNumber() {
        for (Protocol p : Protocol.values()) {
            SimulationResult r = run(p, cfg(25, 3));
            int space = p.sequenceSpace(3);
            Set<Integer> ids = new HashSet<>();
            for (SimulationEvent e : r.events()) {
                if (e.kind() == Kind.TRANSMIT) { ids.add(e.packetId()); assertEquals(e.packetId() % space, e.seqNum()); }
            }
            assertEquals(25, ids.size());
            for (int i = 0; i < 25; i++) assertTrue(ids.contains(i));
        }
    }

    // ------------------------------------------------------------ scenario 3: one lost data packet
    @Test void goBackNResendsWholeWindowAfterOneLostPacket() {
        SimulationResult r = run(Protocol.GO_BACK_N, cfg(10, 4), new ScriptedChannel().loseData(2, 0));
        assertEquals(10, r.delivered());
        assertTrue(r.retransmissions() >= 2, "GBN must resend packet 2 AND the later outstanding packets");
        assertTrue(r.timeouts() >= 1);
        assertTrue(count(r, Kind.DATA_REJECTED) >= 1, "receiver must reject out-of-order packets");
        assertEquals(0, count(r, Kind.DATA_BUFFERED), "GBN receiver never buffers");
        assertTrue(count(r, Kind.ACK_DUPLICATE) >= 1, "re-ACKs of last in-order packet appear as duplicates at sender");
    }

    @Test void selectiveRepeatResendsOnlyTheLostPacket() {
        SimulationResult r = run(Protocol.SELECTIVE_REPEAT, cfg(10, 4), new ScriptedChannel().loseData(2, 0));
        assertEquals(10, r.delivered());
        assertEquals(1, r.retransmissions());
        assertEquals(1, r.timeouts());
        assertTrue(count(r, Kind.DATA_BUFFERED) >= 1, "later packets are buffered while 2 is missing");
        // packets 3.. must be delivered only after packet 2 (in-order delivery to the application)
        long d2 = delivery(r, 2), d3 = delivery(r, 3);
        assertTrue(d3 >= d2);
    }

    @Test void selectiveRepeatBeatsGoBackNOnRetransmissionsForSingleLoss() {
        ScriptedChannel ch1 = new ScriptedChannel().loseData(5, 0), ch2 = new ScriptedChannel().loseData(5, 0);
        SimulationResult g = run(Protocol.GO_BACK_N, cfg(30, 8), ch1);
        SimulationResult s = run(Protocol.SELECTIVE_REPEAT, cfg(30, 8), ch2);
        assertTrue(g.retransmissions() > s.retransmissions());
    }

    // ------------------------------------------------------------ scenario 4: one lost ACK
    @Test void goBackNCumulativeAckCoversLostAck() {
        // ACK for packet 0 is lost, but the cumulative ACK for packet 1 arrives before the timeout.
        SimulationResult r = run(Protocol.GO_BACK_N, cfg(5, 4), new ScriptedChannel().loseAckFor(0, 0));
        assertEquals(5, r.delivered());
        assertEquals(0, r.retransmissions());
        assertEquals(1, r.lostAcks());
    }

    @Test void selectiveRepeatRetransmitsAfterLostAckAndReAcksDuplicate() {
        SimulationResult r = run(Protocol.SELECTIVE_REPEAT, cfg(5, 4), new ScriptedChannel().loseAckFor(0, 0));
        assertEquals(5, r.delivered());
        assertEquals(1, r.retransmissions());
        assertTrue(count(r, Kind.DATA_DUPLICATE) >= 1, "receiver recognises the duplicate and re-ACKs");
        assertEquals(1, r.lostAcks());
    }

    @Test void goBackNLostFinalAckForcesRetransmission() {
        // single packet, its only ACK lost: nothing can cover it, so a timeout/retransmission is required
        SimulationResult r = run(Protocol.GO_BACK_N, cfg(1, 4), new ScriptedChannel().loseAckFor(0, 0));
        assertEquals(1, r.retransmissions());
        assertEquals(1, r.delivered());
        assertEquals(1, r.timeouts());
    }

    // ------------------------------------------------------------ scenario 5: high loss
    @Test void highLossStillDeliversEverything() {
        for (Protocol p : Protocol.values()) {
            SimulationConfig c = cfg(100, 8).lossProbability(0.5).seed(11);
            SimulationResult r = run(p, c);
            assertEquals(Status.COMPLETED, r.status());
            assertEquals(100, r.delivered());
            assertTrue(r.retransmissions() > 0);
            assertTrue(r.lostPackets() > 0);
            assertTrue(r.efficiencyPct() < 100.0);
        }
    }

    // ------------------------------------------------------------ scenario 6: high propagation delay
    @Test void highPropagationDelaySlowsCompletionProportionally() {
        for (Protocol p : Protocol.values()) {
            SimulationConfig slow = cfg(10, 2).propagationDelayMs(1000).timeoutMs(3000);
            SimulationConfig fast = cfg(10, 2);
            SimulationResult rs = run(p, slow), rf = run(p, fast);
            double rtt = slow.roundTripMs();
            assertTrue(rs.completionTimeMs() >= 4 * rtt, "10 packets / window 2 needs about 5 RTTs");
            assertTrue(rs.throughputBps() < rf.throughputBps() / 5);
            assertEquals(0, rs.retransmissions());
        }
    }

    // ------------------------------------------------------------ scenario 7: window sizes
    @Test void stopAndWaitWindowOne() {
        for (Protocol p : Protocol.values()) {
            SimulationResult r = run(p, cfg(5, 1));
            assertEquals(5 * 109.0, r.completionTimeMs(), 1e-9);
            assertEquals(5, r.delivered());
        }
    }

    @Test void windowLargerThanPacketCountSendsEverythingInOneBurst() {
        for (Protocol p : Protocol.values()) {
            SimulationResult r = run(p, cfg(50, 100));
            // last packet starts at 49*8 = 392, ends 400, arrives 450, ACK back at 501
            assertEquals(501.0, r.completionTimeMs(), 1e-9);
            assertEquals(0, r.retransmissions());
        }
    }

    @Test void largerWindowImprovesThroughputWhenLossless() {
        for (Protocol p : Protocol.values()) {
            double small = run(p, cfg(100, 2)).throughputBps();
            double large = run(p, cfg(100, 16)).throughputBps();
            assertTrue(large > small);
        }
    }

    // ------------------------------------------------------------ scenario 8: corruption
    @Test void corruptedPacketIsDiscardedAndRetransmitted() {
        for (Protocol p : Protocol.values()) {
            SimulationResult r = run(p, cfg(6, 3), new ScriptedChannel().corruptData(1, 0));
            assertEquals(6, r.delivered());
            assertEquals(1, r.corruptedPackets());
            assertEquals(0, r.lostPackets());
            assertEquals(1, count(r, Kind.DATA_CORRUPTED));
            assertTrue(r.retransmissions() >= 1);
        }
    }

    @Test void randomCorruptionIsCountedSeparatelyFromLoss() {
        SimulationConfig c = cfg(100, 8).corruptionProbability(0.3);
        SimulationResult r = run(Protocol.SELECTIVE_REPEAT, c);
        assertTrue(r.corruptedPackets() > 0);
        assertEquals(0, r.lostPackets());
        assertEquals(100, r.delivered());
    }

    // ------------------------------------------------------------ timers & ordering
    @Test void staleTimersAreIgnoredAndCauseNoRetransmission() {
        for (Protocol p : Protocol.values()) {
            SimulationResult r = run(p, cfg(30, 2));
            assertEquals(0, r.timeouts());
            assertEquals(0, r.retransmissions());
            if (p == Protocol.SELECTIVE_REPEAT) assertTrue(count(r, Kind.STALE_TIMER) > 0);
        }
    }

    @Test void ackArrivingExactlyAtTimeoutPreventsTimeout() {
        // single packet: timer = 8 + 101 = 109 ms == ACK arrival time; arrivals are processed first
        for (Protocol p : Protocol.values()) {
            SimulationResult r = run(p, cfg(1, 1).timeoutMs(101));
            assertEquals(0, r.timeouts());
            assertEquals(0, r.retransmissions());
        }
    }

    @Test void prematureTimeoutCausesSpuriousRetransmissionButStillCompletes() {
        for (Protocol p : Protocol.values()) {
            SimulationResult r = run(p, cfg(10, 4).timeoutMs(60));
            assertEquals(10, r.delivered());
            assertTrue(r.retransmissions() > 0);
        }
    }

    // ------------------------------------------------------------ window advancement
    @Test void windowBaseAdvancesMonotonicallyAndEndsAtTotal() {
        for (Protocol p : Protocol.values()) {
            SimulationResult r = run(p, cfg(40, 5).lossProbability(0.2).seed(5));
            int last = 0;
            for (SimulationEvent e : r.events()) {
                if (e.kind() == Kind.WINDOW_SLIDE) { assertTrue(e.packetId() > last); last = e.packetId(); }
            }
            assertEquals(40, last);
        }
    }

    // ------------------------------------------------------------ wraparound / fuzz correctness
    @Test void wraparoundFuzzAlwaysDeliversAllPacketsInOrder() {
        int[] windows = {1, 2, 3, 5, 8};
        for (Protocol p : Protocol.values()) {
            for (int w : windows) {
                for (long seed = 1; seed <= 40; seed++) {
                    SimulationConfig c = cfg(60, w).lossProbability(0.25).corruptionProbability(0.1).seed(seed);
                    SimulationResult r = run(p, c);
                    String ctx = p + " W=" + w + " seed=" + seed;
                    assertEquals(Status.COMPLETED, r.status(), ctx);
                    assertEquals(60, r.delivered(), ctx);
                    long prev = -1;
                    for (SimulationEvent e : r.events()) {
                        if (e.kind() == Kind.DELIVER) { assertEquals(prev + 1, e.packetId(), ctx + " in-order delivery"); prev = e.packetId(); }
                    }
                    assertEquals(59, prev, ctx);
                    assertTrue(r.transmissions() >= 60, ctx);
                }
            }
        }
    }

    // ------------------------------------------------------------ termination
    @Test void eventLimitTerminatesSimulationAndMarksResultInvalid() {
        SimulationConfig c = cfg(5000, 8).maxEvents(1000);
        SimulationResult r = run(Protocol.GO_BACK_N, c);
        assertEquals(Status.ABORTED_EVENT_LIMIT, r.status());
        assertFalse(r.isValid());
        assertTrue(r.delivered() < 5000);
    }

    @Test void retryLimitAbortsSessionAndReportsPartialDelivery() {
        for (Protocol p : Protocol.values()) {
            SimulationConfig c = cfg(10, 4).maxRetries(3);
            SimulationResult r = run(p, c, new ScriptedChannel().loseAllAttempts(0));
            assertEquals(Status.ABORTED_RETRY_LIMIT, r.status());
            assertTrue(r.isValid());
            assertTrue(r.deliveryRatioPct() < 100.0);
        }
    }

    @Test void metricsAreSafeWhenNothingIsDelivered() {
        SimulationConfig c = cfg(1, 1).maxRetries(2);
        SimulationResult r = run(Protocol.GO_BACK_N, c, new ScriptedChannel().loseAllAttempts(0));
        assertEquals(0, r.delivered());
        assertEquals(0.0, r.deliveryRatioPct(), 1e-12);
        assertTrue(Double.isNaN(r.avgDelayMs()));
        assertEquals(0.0, r.throughputBps(), 1e-12);
        assertEquals(0.0, r.efficiencyPct(), 1e-12);
        assertFalse(Double.isNaN(r.completionTimeMs()));
    }

    // ------------------------------------------------------------ reproducibility
    @Test void sameSeedReproducesIdenticalRunsAndDifferentSeedsDiffer() {
        for (Protocol p : Protocol.values()) {
            SimulationConfig c = cfg(80, 6).lossProbability(0.2).corruptionProbability(0.1).seed(2024);
            SimulationResult a = run(p, c), b = run(p, c);
            assertEquals(a.events(), b.events());
            assertEquals(a.completionTimeMs(), b.completionTimeMs(), 0.0);
            assertEquals(a.transmissions(), b.transmissions());
            boolean differs = false;
            for (long s = 1; s <= 10 && !differs; s++) differs = run(p, c.copy().seed(s)).transmissions() != a.transmissions();
            assertTrue(differs);
        }
    }

    // ------------------------------------------------------------ metric definitions
    @Test void metricsFollowDocumentedDefinitions() {
        SimulationConfig c = cfg(60, 6).lossProbability(0.15).seed(9);
        for (Protocol p : Protocol.values()) {
            SimulationResult r = run(p, c);
            double bits = c.getPayloadBytes() * 8.0;
            assertEquals(r.delivered() * 100.0 / r.totalPackets(), r.deliveryRatioPct(), 1e-9);
            assertEquals(r.delivered() * bits / (r.completionTimeMs() / 1000.0), r.throughputBps(), 1e-6);
            assertEquals(r.delivered() * 100.0 / r.transmissions(), r.efficiencyPct(), 1e-9);
            assertEquals(r.transmissions() - r.totalPackets(), r.retransmissions());   // all delivered, each sent at least once
            assertTrue(r.channelUtilizationPct() <= 100.0 && r.channelUtilizationPct() > 0.0);
            assertNotEquals(r.channelUtilizationPct(), r.efficiencyPct(), "utilisation and efficiency are distinct metrics");
        }
    }

    private static long delivery(SimulationResult r, int id) {
        return r.events().stream().filter(e -> e.kind() == Kind.DELIVER && e.packetId() == id).findFirst().orElseThrow().timeMicros();
    }
}
