package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.lucene.search.spans.SpanQuery;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

public abstract class HitsSample extends Hits {

    public final static long RANDOM_SEED = Long.MIN_VALUE;

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param hits hits object to wrap
     * @param ratio ratio of hits to select, from 0 (none) to 1 (all)
     * @param seed seed for the random generator, or HitsSample.RANDOM_SEED to use a
     *            randomly chosen seed
     * @return the sample
     */
    public static HitsSample fromHits(Hits hits, float ratio, long seed) {
        // We can later provide an optimized version that uses a HitsSampleCopy or somesuch
        // (this class could save memory by only storing the hits we're interested in)
        return new HitsSampleImpl(hits, ratio, seed);
    }

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param hits hits object to wrap
     * @param number number of hits to select
     * @param seed seed for the random generator, or HitsSample.RANDOM_SEED to use a
     *            randomly chosen seed
     * @return the sample
     */
    public static HitsSample fromHits(Hits hits, int number, long seed) {
        // We can later provide an optimized version that uses a HitsSampleCopy or somesuch
        // (this class could save memory by only storing the hits we're interested in)
        return new HitsSampleImpl(hits, number, seed);
    }

    /**
     * Take a sample of hits by executing a SpanQuery and sampling the results.
     *
     * @param searcher searcher object
     * @param query query to sample
     * @param ratio ratio of hits to select, from 0 (none) to 1 (all)
     * @param seed seed for the random generator, or HitsSample.RANDOM_SEED to use a
     *            randomly chosen seed
     * @param settings settings to use, or null for defaults
     * @return the sample
     */
    public static HitsSample fromSpanQuery(BlackLabIndex searcher, SpanQuery query, float ratio, long seed, HitsSettings settings) {
        // We can later provide an optimized version that uses a HitsSampleSpans or somesuch
        // (this class could save memory by only storing the hits we're interested in)
        if (!(query instanceof BLSpanQuery))
            throw new IllegalArgumentException("Supplied query must be a BLSpanQuery!");
        return new HitsSampleImpl(Hits.fromSpanQuery(searcher, query, settings), ratio, seed);
    }

    /**
     * Take a sample of hits by executing a SpanQuery and sampling the results.
     *
     * @param searcher searcher object
     * @param query query to sample
     * @param number number of hits to select
     * @param seed seed for the random generator, or HitsSample.RANDOM_SEED to use a
     *            randomly chosen seed
     * @param settings settings to use
     * @return the sample
     */
    public static HitsSample fromSpanQuery(BlackLabIndex searcher, SpanQuery query, int number, long seed, HitsSettings settings) {
        // We can later provide an optimized version that uses a HitsSampleSpans or somesuch
        // (this class could save memory by only storing the hits we're interested in)
        if (!(query instanceof BLSpanQuery))
            throw new IllegalArgumentException("Supplied query must be a BLSpanQuery!");
        return new HitsSampleImpl(Hits.fromSpanQuery(searcher, query, settings), number, seed);
    }

    protected static long getRandomSeed() {
        Random random = new Random();
        return random.nextLong();
    }

    protected float ratioOfHitsToSelect;

    protected int numberOfHitsToSelect;

    protected boolean exactNumberGiven = false;

    protected long seed;

    protected Random random;

    protected HitsSample(BlackLabIndex searcher, AnnotatedField field, float ratio, long seed, HitsSettings settings) {
        super(searcher, field, new ArrayList<Hit>(), settings);
        this.ratioOfHitsToSelect = ratio;
        this.seed = seed == RANDOM_SEED ? getRandomSeed() : seed;
        this.random = new Random(seed);
    }

    protected HitsSample(BlackLabIndex searcher, AnnotatedField field, int number, long seed, HitsSettings settings) {
        super(searcher, field, new ArrayList<Hit>(), settings);
        this.numberOfHitsToSelect = number;
        exactNumberGiven = true;
        this.seed = seed == RANDOM_SEED ? getRandomSeed() : seed;
        this.random = new Random(seed);
    }

    protected HitsSample(BlackLabIndex searcher, AnnotatedField field, List<Hit> hits, float ratio, long seed, HitsSettings settings) {
        super(searcher, field, hits, settings);
        this.ratioOfHitsToSelect = ratio;
        this.seed = seed == RANDOM_SEED ? getRandomSeed() : seed;
        this.random = new Random(seed);
    }

    public float ratio() {
        return ratioOfHitsToSelect;
    }

    public long seed() {
        return seed;
    }

    public int numberOfHitsToSelect() {
        return numberOfHitsToSelect;
    }

    public boolean exactNumberGiven() {
        return exactNumberGiven;
    }

    @Override
    public String toString() {
        return "HitsSample#" + hitsObjId;
    }

}
