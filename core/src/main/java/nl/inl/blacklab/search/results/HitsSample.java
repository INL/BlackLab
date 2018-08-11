package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.Random;

public abstract class HitsSample extends HitsList {

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
    public static Hits fromHits(Hits hits, float ratio, long seed) {
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

    protected static long getRandomSeed() {
        Random random = new Random();
        return random.nextLong();
    }

    protected float ratioOfHitsToSelect;

    protected int numberOfHitsToSelect;

    protected boolean exactNumberGiven = false;

    protected long seed;

    protected Random random;

    protected HitsSample(QueryInfo queryInfo, float ratio, long seed) {
        super(queryInfo, new ArrayList<Hit>());
        this.ratioOfHitsToSelect = ratio;
        this.seed = seed == RANDOM_SEED ? getRandomSeed() : seed;
        this.random = new Random(seed);
    }

    protected HitsSample(QueryInfo queryInfo, int number, long seed) {
        super(queryInfo, new ArrayList<Hit>());
        this.numberOfHitsToSelect = number;
        exactNumberGiven = true;
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
        return "HitsSample#" + resultsObjId();
    }

}
