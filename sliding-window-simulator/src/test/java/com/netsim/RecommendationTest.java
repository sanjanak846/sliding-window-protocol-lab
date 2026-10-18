package com.netsim;

import com.netsim.analysis.*;
import com.netsim.analysis.Recommendation.Decision;
import com.netsim.sim.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RecommendationTest {

    /** Builds a synthetic result so the decision logic can be tested in isolation. */
    private static SimulationResult fake(Protocol p, double pdr, double delayMs, double throughputKbps, double eff, long retx, Status st) {
        return new SimulationResult(p, new SimulationConfig(), st, st.description(), 100, (int) Math.round(pdr), pdr,
                100 + retx, retx, 100, 0, 0, 0, 0, 0, 1000, delayMs, throughputKbps * 1000.0, eff, 50, 0, List.of(), false);
    }
    private static SimulationResult g(double pdr, double d, double t, double e, long r) { return fake(Protocol.GO_BACK_N, pdr, d, t, e, r, Status.COMPLETED); }
    private static SimulationResult s(double pdr, double d, double t, double e, long r) { return fake(Protocol.SELECTIVE_REPEAT, pdr, d, t, e, r, Status.COMPLETED); }

    @Test void onlySelectiveRepeatSatisfiesRequirements() {
        Requirements q = new Requirements(); q.minThroughputKbps = 100.0;
        Recommendation r = RecommendationEngine.evaluate(g(100, 80, 60, 70, 40), s(100, 70, 120, 90, 10), q);
        assertEquals(Decision.RECOMMEND_SR, r.decision());
        assertEquals(Protocol.SELECTIVE_REPEAT, r.recommended());
        assertFalse(r.gbn().eligible());
        assertTrue(r.sr().eligible());
        assertEquals("Throughput", r.gbn().violations().get(0).name());
        assertTrue(r.explanation().contains("60.00"), "explanation must quote the measured GBN throughput");
    }

    @Test void onlyGoBackNSatisfiesRequirements() {
        Requirements q = new Requirements(); q.maxRetransmissions = 20.0;
        Recommendation r = RecommendationEngine.evaluate(g(100, 80, 60, 90, 15), s(100, 70, 120, 70, 35), q);
        assertEquals(Decision.RECOMMEND_GBN, r.decision());
        assertFalse(r.sr().eligible());
    }

    @Test void bothSatisfyAndWeightedScoreDecides() {
        Requirements q = new Requirements(); q.minThroughputKbps = 10.0; q.maxAvgDelayMs = 500.0;
        Recommendation r = RecommendationEngine.evaluate(g(100, 200, 60, 40, 80), s(100, 100, 100, 70, 20), q);
        assertTrue(r.gbn().eligible() && r.sr().eligible());
        assertEquals(Decision.RECOMMEND_SR, r.decision());
        assertTrue(r.sr().score() > r.gbn().score());
    }

    @Test void weightsCanFlipTheWinnerAmongEligibleProtocols() {
        SimulationResult gb = g(100, 100, 100, 50, 50), sr = s(100, 100, 80, 50, 5);
        Requirements throughputFocus = new Requirements();
        throughputFocus.weightThroughput = 10; throughputFocus.weightRetransmissions = 0.1; throughputFocus.weightDelay = 0; throughputFocus.weightEfficiency = 0;
        assertEquals(Decision.RECOMMEND_GBN, RecommendationEngine.evaluate(gb, sr, throughputFocus).decision());
        Requirements retransFocus = new Requirements();
        retransFocus.weightThroughput = 0.1; retransFocus.weightRetransmissions = 10; retransFocus.weightDelay = 0; retransFocus.weightEfficiency = 0;
        assertEquals(Decision.RECOMMEND_SR, RecommendationEngine.evaluate(gb, sr, retransFocus).decision());
    }

    @Test void higherScoreNeverOverridesViolatedHardConstraint() {
        // GBN is better on every soft metric but violates the hard delay limit -> SR must win
        Requirements q = new Requirements(); q.maxAvgDelayMs = 100.0;
        Recommendation r = RecommendationEngine.evaluate(g(100, 150, 200, 90, 1), s(100, 90, 50, 30, 60), q);
        assertTrue(r.gbn().score() > r.sr().score());
        assertEquals(Decision.RECOMMEND_SR, r.decision());
    }

    @Test void neitherSatisfiesRequirementsAndViolationsAreReported() {
        Requirements q = new Requirements(); q.minThroughputKbps = 500.0; q.minDeliveryRatioPct = 99.0;
        Recommendation r = RecommendationEngine.evaluate(g(100, 80, 60, 70, 40), s(98, 70, 120, 90, 10), q);
        assertEquals(Decision.NEITHER, r.decision());
        assertNull(r.recommended());
        assertEquals(1, r.gbn().violations().size());
        assertEquals(2, r.sr().violations().size());
        assertTrue(r.explanation().contains("Neither"));
    }

    @Test void nearlyIdenticalResultsAreReportedAsTie() {
        Requirements q = new Requirements();
        Recommendation r = RecommendationEngine.evaluate(g(100, 100, 100, 50, 10), s(100, 100, 100.5, 50, 10), q);
        assertEquals(Decision.TIE, r.decision());
        assertNull(r.recommended());
    }

    @Test void invalidSimulationIsAlwaysDisqualified() {
        Requirements q = new Requirements();
        SimulationResult bad = fake(Protocol.SELECTIVE_REPEAT, 100, 10, 999, 99, 0, Status.ABORTED_EVENT_LIMIT);
        Recommendation r = RecommendationEngine.evaluate(g(100, 100, 50, 50, 10), bad, q);
        assertEquals(Decision.RECOMMEND_GBN, r.decision());
        assertEquals("Simulation validity", r.sr().violations().get(0).name());
    }

    @Test void undefinedDelayFailsDelayConstraint() {
        Requirements q = new Requirements(); q.maxAvgDelayMs = 1000.0; q.minDeliveryRatioPct = 0.0;
        List<ConstraintCheck> checks = RecommendationEngine.check(g(0, Double.NaN, 0, 0, 0), q);
        assertTrue(checks.stream().anyMatch(c -> c.name().equals("Average delay") && !c.satisfied()));
    }

    @Test void unspecifiedConstraintsAreNotChecked() {
        Requirements q = new Requirements(); q.minDeliveryRatioPct = null;
        assertEquals(1, RecommendationEngine.check(g(50, 1, 1, 1, 1), q).size()); // only validity
    }

    @Test void scoringNormalisation() {
        Requirements q = new Requirements(); // equal weights
        double[] sc = RecommendationEngine.score(g(100, 200, 50, 40, 20), s(100, 100, 100, 80, 10), q);
        // GBN: thr 0.5, delay 100/200=0.5, retx 10/20=0.5, eff 0.5 -> 0.5 ; SR: all 1.0
        assertEquals(0.5, sc[0], 1e-9);
        assertEquals(1.0, sc[1], 1e-9);
        double[] equalZero = RecommendationEngine.score(g(100, 100, 100, 100, 0), s(100, 100, 100, 100, 0), q);
        assertEquals(1.0, equalZero[0], 1e-9);
        assertEquals(1.0, equalZero[1], 1e-9);
    }

    @Test void requirementsValidation() {
        Requirements q = new Requirements(); q.minDeliveryRatioPct = 120.0;
        assertFalse(q.validate().isEmpty());
        q = new Requirements(); q.weightDelay = -1;
        assertFalse(q.validate().isEmpty());
        q = new Requirements(); q.weightDelay = q.weightEfficiency = q.weightRetransmissions = q.weightThroughput = 0;
        assertFalse(q.validate().isEmpty());
        q = new Requirements(); q.maxAvgDelayMs = -5.0;
        assertFalse(q.validate().isEmpty());
        assertTrue(new Requirements().validate().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> RecommendationEngine.evaluate(new SimulationConfig().totalPackets(0), new Requirements()));
    }

    // ---------------- end-to-end with the real simulator
    @Test void endToEndLosslessGivesIdenticalProtocolsSoTie() {
        SimulationConfig c = TestSupport.cfg(40, 4);
        Recommendation r = RecommendationEngine.evaluate(c, new Requirements());
        assertEquals(Decision.TIE, r.decision());
    }

    @Test void endToEndOnlyOneProtocolMeetsRetransmissionLimit() {
        SimulationConfig c = TestSupport.cfg(100, 8).lossProbability(0.2).seed(21);
        SimulationResult gr = SimulationRunner.run(Protocol.GO_BACK_N, c), sr = SimulationRunner.run(Protocol.SELECTIVE_REPEAT, c);
        assertTrue(gr.retransmissions() > sr.retransmissions(), "precondition: GBN resends more under loss");
        Requirements q = new Requirements();
        q.maxRetransmissions = (gr.retransmissions() + sr.retransmissions()) / 2.0;
        Recommendation r = RecommendationEngine.evaluate(c, q);
        assertEquals(Decision.RECOMMEND_SR, r.decision());
        assertFalse(r.gbn().eligible());
    }

    @Test void endToEndNeitherMeetsImpossibleThroughput() {
        SimulationConfig c = TestSupport.cfg(50, 4).lossProbability(0.1);
        Requirements q = new Requirements(); q.minThroughputKbps = 5000.0; // link is only 1 Mbit/s
        assertEquals(Decision.NEITHER, RecommendationEngine.evaluate(c, q).decision());
    }

    @Test void endToEndBothMeetRelaxedRequirements() {
        SimulationConfig c = TestSupport.cfg(50, 8).lossProbability(0.05);
        Requirements q = new Requirements(); q.minThroughputKbps = 1.0; q.maxAvgDelayMs = 100000.0; q.minEfficiencyPct = 1.0;
        Recommendation r = RecommendationEngine.evaluate(c, q);
        assertTrue(r.gbn().eligible() && r.sr().eligible());
    }
}
