package nl.inl.blacklab.search;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.apache.lucene.search.spans.SpanQuery;

import nl.inl.blacklab.search.lucene.BLSpanQuery;

public abstract class HitsSample extends HitsImpl {

	public final static long RANDOM_SEED = Long.MIN_VALUE;

	protected static long getRandomSeed() {
		Random random = new Random();
		return random.nextLong();
	}

	protected float ratioOfHitsToSelect;

	protected int numberOfHitsToSelect;

	protected boolean exactNumberGiven = false;

	protected long seed;

	protected Random random;

	protected HitsSample(Searcher searcher, float ratio, long seed) {
		super(searcher, new ArrayList<Hit>());
		this.ratioOfHitsToSelect = ratio;
		this.seed = seed == RANDOM_SEED ? getRandomSeed() : seed;
		this.random = new Random(seed);
	}

	protected HitsSample(Searcher searcher, int number, long seed) {
		super(searcher, new ArrayList<Hit>());
		this.numberOfHitsToSelect = number;
		exactNumberGiven = true;
		this.seed = seed == RANDOM_SEED ? getRandomSeed() : seed;
		this.random = new Random(seed);
	}

	protected HitsSample(Searcher searcher, List<Hit> hits, float ratio, long seed) {
		super(searcher, hits);
		this.ratioOfHitsToSelect = ratio;
		this.seed = seed == RANDOM_SEED ? getRandomSeed() : seed;
		this.random = new Random(seed);
	}

	/**
	 * Take a sample of hits by wrapping an existing Hits object.
	 *
	 * @param hits hits object to wrap
	 * @param ratio ratio of hits to select, from 0 (none) to 1 (all)
	 * @param seed seed for the random generator, or HitsSample.RANDOM_SEED to use a randomly chosen seed
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
	 * @param seed seed for the random generator, or HitsSample.RANDOM_SEED to use a randomly chosen seed
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
	 * @param seed seed for the random generator, or HitsSample.RANDOM_SEED to use a randomly chosen seed
	 * @return the sample
	 */
	public static HitsSample fromSpanQuery(Searcher searcher, SpanQuery query, float ratio, long seed) {
		// We can later provide an optimized version that uses a HitsSampleSpans or somesuch
		// (this class could save memory by only storing the hits we're interested in)
		if (!(query instanceof BLSpanQuery))
			throw new IllegalArgumentException("Supplied query must be a BLSpanQuery!");
		return new HitsSampleImpl(Hits.fromSpanQuery(searcher, query), ratio, seed);
	}

	/**
	 * Take a sample of hits by executing a SpanQuery and sampling the results.
	 *
	 * @param searcher searcher object
	 * @param query query to sample
	 * @param number number of hits to select
	 * @param seed seed for the random generator, or HitsSample.RANDOM_SEED to use a randomly chosen seed
	 * @return the sample
	 */
	public static HitsSample fromSpanQuery(Searcher searcher, SpanQuery query, int number, long seed) {
		// We can later provide an optimized version that uses a HitsSampleSpans or somesuch
		// (this class could save memory by only storing the hits we're interested in)
		if (!(query instanceof BLSpanQuery))
			throw new IllegalArgumentException("Supplied query must be a BLSpanQuery!");
		return new HitsSampleImpl(Hits.fromSpanQuery(searcher, query), number, seed);
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

}
