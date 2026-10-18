package com.netsim.analysis;

import com.netsim.analysis.Recommendation.Decision;
import com.netsim.sim.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Requirement-based protocol selection (see docs/RECOMMENDATION.md).
 *
 * 1 validate; 2 simulate both under identical conditions; 3 measure; 4 check each hard constraint;
 * 5 eliminate violators; 6 single survivor wins; 7 two survivors are ranked by a weighted, normalised
 * score (tie if within the tie margin); 8 no survivors -> NEITHER with the violated constraints;
 * 9 the explanation quotes measured values.
 */
public final class RecommendationEngine {
    private RecommendationEngine() { }

    /** Runs both protocols on the same configuration and evaluates them. */
    public static Recommendation evaluate(SimulationConfig cfg, Requirements req) {
        List<String> errors = new ArrayList<>(cfg.validate());
        errors.addAll(req.validate());
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join(" ", errors));
        SimulationConfig run = cfg.copy().recordEvents(false);
        SimulationResult g = SimulationRunner.run(Protocol.GO_BACK_N, run);
        SimulationResult s = SimulationRunner.run(Protocol.SELECTIVE_REPEAT, run);
        return evaluate(g, s, req);
    }

    /** Pure decision logic over two finished results (unit-testable without simulation). */
    public static Recommendation evaluate(SimulationResult gbn, SimulationResult sr, Requirements req) {
        List<String> errors = req.validate();
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join(" ", errors));

        List<ConstraintCheck> gChecks = check(gbn, req);
        List<ConstraintCheck> sChecks = check(sr, req);
        boolean gOk = gChecks.stream().allMatch(ConstraintCheck::satisfied);
        boolean sOk = sChecks.stream().allMatch(ConstraintCheck::satisfied);
        double[] scores = score(gbn, sr, req);
        ProtocolEvaluation ge = new ProtocolEvaluation(Protocol.GO_BACK_N, gbn, gChecks, gOk, scores[0]);
        ProtocolEvaluation se = new ProtocolEvaluation(Protocol.SELECTIVE_REPEAT, sr, sChecks, sOk, scores[1]);

        StringBuilder sb = new StringBuilder();
        Decision decision;
        Protocol rec = null;

        if (gOk && !sOk) {
            decision = Decision.RECOMMEND_GBN; rec = Protocol.GO_BACK_N;
            sb.append("Only Go-Back-N satisfies all hard requirements.\n");
            sb.append(violationText(se));
            sb.append(measured(gbn));
        } else if (sOk && !gOk) {
            decision = Decision.RECOMMEND_SR; rec = Protocol.SELECTIVE_REPEAT;
            sb.append("Only Selective Repeat satisfies all hard requirements.\n");
            sb.append(violationText(ge));
            sb.append(measured(sr));
        } else if (!gOk) {
            decision = Decision.NEITHER;
            sb.append("Neither protocol meets all hard requirements.\n");
            sb.append(violationText(ge)).append(violationText(se));
            sb.append("Consider a larger window, a longer timeout, or relaxing the violated constraints.\n");
        } else {
            double diff = scores[1] - scores[0];
            sb.append(String.format("Both protocols satisfy all hard requirements, so they are ranked by weighted score (GBN %.3f, SR %.3f, tie margin %.3f).%n",
                    scores[0], scores[1], req.tieMargin));
            if (Math.abs(diff) < req.tieMargin) {
                decision = Decision.TIE;
                sb.append("The scores are within the tie margin: no unjustified winner is declared. Trade-offs:\n");
                sb.append(tradeoffs(gbn, sr));
                sb.append("If both are acceptable, Go-Back-N is simpler to implement (no receiver buffering, single timer).\n");
            } else if (diff > 0) {
                decision = Decision.RECOMMEND_SR; rec = Protocol.SELECTIVE_REPEAT;
                sb.append("Selective Repeat scores higher.\n").append(tradeoffs(gbn, sr));
            } else {
                decision = Decision.RECOMMEND_GBN; rec = Protocol.GO_BACK_N;
                sb.append("Go-Back-N scores higher.\n").append(tradeoffs(gbn, sr));
            }
        }
        return new Recommendation(decision, rec, ge, se, sb.toString());
    }

    /** Evaluates every specified hard constraint. Invalid runs always fail the validity check. */
    public static List<ConstraintCheck> check(SimulationResult r, Requirements q) {
        List<ConstraintCheck> out = new ArrayList<>();
        out.add(new ConstraintCheck("Simulation validity", "must end normally", r.isValid() ? 1 : 0, r.isValid(),
                r.isValid() ? "run ended normally (" + r.status() + ")" : "run invalid: " + r.statusDetail()));
        if (q.minDeliveryRatioPct != null)
            out.add(atLeast("Delivery ratio", q.minDeliveryRatioPct, r.deliveryRatioPct(), "%"));
        if (q.maxAvgDelayMs != null) {
            double d = r.avgDelayMs();
            boolean ok = !Double.isNaN(d) && d <= q.maxAvgDelayMs;
            out.add(new ConstraintCheck("Average delay", "<= " + fmt(q.maxAvgDelayMs) + " ms", d, ok,
                    Double.isNaN(d) ? "no packet delivered, delay undefined" : String.format("measured %.2f ms vs limit %.2f ms", d, q.maxAvgDelayMs)));
        }
        if (q.minThroughputKbps != null) out.add(atLeast("Throughput", q.minThroughputKbps, r.throughputKbps(), " kbit/s"));
        if (q.minEfficiencyPct != null) out.add(atLeast("Efficiency", q.minEfficiencyPct, r.efficiencyPct(), "%"));
        if (q.maxRetransmissions != null) {
            boolean ok = r.retransmissions() <= q.maxRetransmissions;
            out.add(new ConstraintCheck("Retransmissions", "<= " + fmt(q.maxRetransmissions), r.retransmissions(), ok,
                    String.format("measured %d vs limit %.0f", r.retransmissions(), q.maxRetransmissions)));
        }
        return out;
    }

    private static ConstraintCheck atLeast(String name, double limit, double value, String unit) {
        boolean ok = value >= limit;
        return new ConstraintCheck(name, ">= " + fmt(limit) + unit, value, ok,
                String.format("measured %.2f%s vs required %.2f%s", value, unit, limit, unit));
    }

    /**
     * Normalised weighted score in [0,1] per protocol. Benefit metrics (throughput, efficiency):
     * v / max(vG, vS). Cost metrics (delay, retransmissions): min(vG, vS) / v. Equal zeros score 1;
     * an undefined (NaN) delay scores 0. Score = sum(w_i * s_i) / sum(w_i).
     */
    public static double[] score(SimulationResult g, SimulationResult s, Requirements q) {
        double wSum = q.weightThroughput + q.weightDelay + q.weightRetransmissions + q.weightEfficiency;
        if (wSum <= 0) return new double[]{0, 0};
        double[] t = benefit(g.throughputBps(), s.throughputBps());
        double[] d = cost(g.avgDelayMs(), s.avgDelayMs());
        double[] r = cost(g.retransmissions(), s.retransmissions());
        double[] e = benefit(g.efficiencyPct(), s.efficiencyPct());
        double sg = (q.weightThroughput * t[0] + q.weightDelay * d[0] + q.weightRetransmissions * r[0] + q.weightEfficiency * e[0]) / wSum;
        double ss = (q.weightThroughput * t[1] + q.weightDelay * d[1] + q.weightRetransmissions * r[1] + q.weightEfficiency * e[1]) / wSum;
        return new double[]{sg, ss};
    }

    static double[] benefit(double a, double b) {
        double max = Math.max(a, b);
        if (max <= 0) return new double[]{1, 1};
        return new double[]{a / max, b / max};
    }

    static double[] cost(double a, double b) {
        boolean na = Double.isNaN(a), nb = Double.isNaN(b);
        if (na && nb) return new double[]{0, 0};
        if (na) return new double[]{0, 1};
        if (nb) return new double[]{1, 0};
        double min = Math.min(a, b);
        if (min <= 0) return new double[]{a <= 0 ? 1 : 0, b <= 0 ? 1 : 0};
        return new double[]{min / a, min / b};
    }

    private static String violationText(ProtocolEvaluation e) {
        StringBuilder sb = new StringBuilder();
        sb.append(e.protocol().displayName()).append(" violates: ");
        List<ConstraintCheck> v = e.violations();
        for (int i = 0; i < v.size(); i++) {
            if (i > 0) sb.append("; ");
            sb.append(v.get(i).name()).append(" (").append(v.get(i).text()).append(")");
        }
        return sb.append(".\n").toString();
    }

    private static String measured(SimulationResult r) {
        return String.format("%s measured: delivery %.2f%%, avg delay %s ms, throughput %.2f kbit/s, efficiency %.2f%%, retransmissions %d.%n",
                r.protocol().displayName(), r.deliveryRatioPct(),
                Double.isNaN(r.avgDelayMs()) ? "n/a" : String.format("%.2f", r.avgDelayMs()),
                r.throughputKbps(), r.efficiencyPct(), r.retransmissions());
    }

    private static String tradeoffs(SimulationResult g, SimulationResult s) { return measured(g) + measured(s); }

    private static String fmt(double v) { return v == Math.rint(v) ? String.valueOf((long) v) : String.format("%.2f", v); }
}
