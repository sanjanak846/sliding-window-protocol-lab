package com.netsim.experiment;

import com.netsim.sim.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

/**
 * Runs a parameter sweep on a thread pool (no JavaFX dependency, so the GUI can call it from a
 * background Task). Trial t of every combination uses seed baseSeed + t for BOTH protocols.
 */
public final class ExperimentRunner {
    private ExperimentRunner() { }

    public static List<ExperimentRow> run(ExperimentSpec spec, IntConsumer progressDone, AtomicBoolean cancel)
            throws InterruptedException {
        List<String> errors = spec.validate();
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join(" ", errors));

        record Combo(double loss, double corr, int window, double delay, int packets) { }
        List<Combo> combos = new ArrayList<>();
        for (double l : spec.lossPct) for (double c : spec.corruptionPct) for (int w : spec.windows)
            for (double d : spec.delaysMs) for (int p : spec.packets) combos.add(new Combo(l, c, w, d, p));

        int threads = Math.max(1, Runtime.getRuntime().availableProcessors());
        ExecutorService pool = Executors.newFixedThreadPool(threads, r -> { Thread t = new Thread(r, "experiment-worker"); t.setDaemon(true); return t; });
        try {
            List<Future<List<ExperimentRow>>> futures = new ArrayList<>();
            java.util.concurrent.atomic.AtomicInteger done = new java.util.concurrent.atomic.AtomicInteger();
            for (Combo c : combos) {
                futures.add(pool.submit(() -> {
                    List<ExperimentRow> rows = new ArrayList<>();
                    if (cancel != null && cancel.get()) return rows;
                    for (Protocol p : spec.protocols) rows.add(runCombo(spec, p, c.loss(), c.corr(), c.window(), c.delay(), c.packets()));
                    if (progressDone != null) progressDone.accept(done.incrementAndGet());
                    return rows;
                }));
            }
            List<ExperimentRow> all = new ArrayList<>();
            for (Future<List<ExperimentRow>> f : futures) {
                try { all.addAll(f.get()); }
                catch (ExecutionException e) { throw new IllegalStateException(e.getCause()); }
                if (cancel != null && cancel.get()) throw new CancellationException("experiment cancelled");
            }
            return all;
        } finally {
            pool.shutdownNow();
        }
    }

    static ExperimentRow runCombo(ExperimentSpec spec, Protocol p, double loss, double corr, int window, double delay, int packets) {
        int n = spec.trials;
        double[] thr = new double[n], pdr = new double[n], ret = new double[n], dly = new double[n], eff = new double[n], cmp = new double[n];
        int valid = 0, delayN = 0, invalid = 0;
        for (int t = 0; t < n; t++) {
            SimulationConfig cfg = spec.configFor(loss, corr, window, delay, packets, spec.baseSeed + t);
            SimulationResult r = SimulationRunner.run(p, cfg);
            if (!r.isValid()) { invalid++; continue; }
            thr[valid] = r.throughputKbps(); pdr[valid] = r.deliveryRatioPct(); ret[valid] = r.retransmissions();
            eff[valid] = r.efficiencyPct(); cmp[valid] = r.completionTimeMs();
            if (!Double.isNaN(r.avgDelayMs())) dly[delayN++] = r.avgDelayMs();
            valid++;
        }
        return new ExperimentRow(p, loss, corr, window, delay, packets, n, invalid,
                ExperimentRow.Stat.of(thr, valid), ExperimentRow.Stat.of(pdr, valid), ExperimentRow.Stat.of(ret, valid),
                ExperimentRow.Stat.of(dly, delayN), ExperimentRow.Stat.of(eff, valid), ExperimentRow.Stat.of(cmp, valid));
    }
}
