package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.Random;

public abstract class HitsSample extends HitsList {

    public final static long RANDOM_SEED = Long.MIN_VALUE;

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param hits hits object to wrap
     * @param parameters sample parameters 
     * @return the sample
     */
    public static Hits fromHits(Hits hits, SampleParameters parameters) {
        // We can later provide an optimized version that uses a HitsSampleCopy or somesuch
        // (this class could save memory by only storing the hits we're interested in)
        return new HitsSampleImpl(hits, parameters);
    }

    private SampleParameters parameters;

    protected long seed;

    protected Random random;

    protected HitsSample(QueryInfo queryInfo, SampleParameters parameters) {
        super(queryInfo, new ArrayList<Hit>());
        this.parameters = parameters;
        this.seed = parameters.seed();
        this.random = new Random(seed);
    }

    public long seed() {
        return seed;
    }
    
    public SampleParameters sampleParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        return "HitsSample#" + resultsObjId();
    }

}
