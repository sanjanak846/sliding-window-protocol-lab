package com.netsim.sim;

/**
 * Stochastic channel model using counter-based hashing instead of a stateful RNG.
 *
 * The fate of data transmission (id, attempt) depends only on (seed, id, attempt), and the fate of an
 * ACK only on (seed, id, attempt) of the data packet that triggered it. Therefore the two protocols
 * experience IDENTICAL channel luck for identical transmissions (common random numbers), and results
 * are reproducible irrespective of event interleaving.
 *
 * Each transmission is first tested for loss with probability p_loss; if it survives, it is corrupted
 * with probability p_corrupt. Effective error probability = p_loss + (1-p_loss)*p_corrupt.
 */
public class Channel {
    private static final int STREAM_DATA = 1;
    private static final int STREAM_ACK = 2;

    private final long seed;
    private final double lossProb;
    private final double corruptProb;

    public Channel(long seed, double lossProb, double corruptProb) {
        this.seed = seed; this.lossProb = lossProb; this.corruptProb = corruptProb;
    }

    public Fate dataFate(int id, int attempt) { return fate(STREAM_DATA, id, attempt); }
    public Fate ackFate(int triggerId, int triggerAttempt) { return fate(STREAM_ACK, triggerId, triggerAttempt); }

    private Fate fate(int stream, int id, int attempt) {
        if (uniform(stream, id, attempt, 0) < lossProb) return Fate.LOST;
        if (uniform(stream, id, attempt, 1) < corruptProb) return Fate.CORRUPTED;
        return Fate.OK;
    }

    /** Deterministic uniform value in [0,1) derived from the inputs (SplitMix64 mixing). */
    double uniform(int stream, long a, long b, long salt) {
        long h = mix(seed + 0x9E3779B97F4A7C15L);
        h = mix(h ^ (stream * 0xD6E8FEB86659FD93L));
        h = mix(h ^ (a * 0xA0761D6478BD642FL + 0x1234567L));
        h = mix(h ^ (b * 0xE7037ED1A0B428DBL + 0x7654321L));
        h = mix(h ^ (salt * 0x8EBC6AF09C88C6E3L + 0x55AA55AAL));
        return (h >>> 11) * 0x1.0p-53;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
