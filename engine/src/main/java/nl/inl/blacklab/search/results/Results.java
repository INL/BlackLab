package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.util.ThreadAborter;

/**
 * A list of results of some type.
 *
 * @param <T> result type, e.g. Hit
 */
public abstract class Results<T, P extends ResultProperty<T>> implements SearchResult, Iterable<T> {

    /** When setting how many hits to retrieve/count/store in group, this means "no limit". */
    public static final int NO_LIMIT = -1;

    /** Id the next Hits instance will get */
    private static int nextHitsObjId = 0;

    private static synchronized int getNextHitsObjId() {
        return nextHitsObjId++;
    }

    // Perform simple generic sampling operation
    protected static <T, P extends ResultProperty<T>> List<T> doSample(ResultsList<T, P> source, SampleParameters sampleParameters) {
        // We can later provide an optimized version that uses a HitsSampleCopy or somesuch
        // (this class could save memory by only storing the hits we're interested in)

        List<T> results = new ArrayList<>();

        Random random = new Random(sampleParameters.seed());
        int numberOfHitsToSelect = sampleParameters.numberOfHits(source.size());
        if (numberOfHitsToSelect > source.size())
            numberOfHitsToSelect = source.size(); // default to all hits in this case
        // Choose the hits
        Set<Integer> chosenHitIndices = new TreeSet<>();
        for (int i = 0; i < numberOfHitsToSelect; i++) {
            // Choose a hit we haven't chosen yet
            int hitIndex;
            do {
                hitIndex = random.nextInt(source.size());
            } while (chosenHitIndices.contains(hitIndex));
            chosenHitIndices.add(hitIndex);
        }

        // Add the hits in order of their index
        for (Integer hitIndex : chosenHitIndices) {
            T hit = source.get(hitIndex);
            results.add(hit);
        }
        return results;
    }

    protected static <T, P extends ResultProperty<T>> List<T> doWindow(ResultsList<T, P> results, int first, int number) {
        if (first < 0 || first != 0 && !results.resultsProcessedAtLeast(first + 1)) {
            //throw new BlackLabRuntimeException("First hit out of range");
            return Collections.emptyList();
        }

        // Auto-clamp number
        int actualSize = number;
        if (!results.resultsProcessedAtLeast(first + actualSize))
            actualSize = results.size() - first;

        // Make sublist (copy results from List.subList() to avoid lingering references large lists)
        return new ArrayList<T>(results.resultsSubList(first, first + actualSize));
    }

//    protected static <T, P extends ResultProperty<T>> List<T> doFilter(ResultsList<T, P> results, ResultProperty<T> property, PropertyValue value) {
//        return results.stream().filter(g -> property.get(g).equals(value)).collect(Collectors.toList());
//    }
//
//    protected static <T, P extends ResultProperty<T>> List<T> doSort(ResultsList<T, P> results, P sortProp) {
//        results.ensureAllResultsRead();
//        List<T> sorted = new ArrayList<>(results.resultsList());
//        sorted.sort(sortProp);
//        return sorted;
//    }

    /** Unique id of this Hits instance (for debugging) */
    protected final int hitsObjId = getNextHitsObjId();

    /** Information about the original query: index, field, max settings, max stats. */
    private QueryInfo queryInfo;

    /**
     * Helper object for pausing threads (making sure queries
     * don't hog the CPU for way too long).
     */
    protected ThreadAborter threadAborter;

    private ResultsStats resultsStats = new ResultsStats() {
        @Override
        public boolean processedAtLeast(int lowerBound) {
            return resultsProcessedAtLeast(lowerBound);
        }

        @Override
        public int processedTotal() {
            return resultsProcessedTotal();
        }

        @Override
        public int processedSoFar() {
            return resultsProcessedSoFar();
        }

        @Override
        public int countedSoFar() {
            return resultsCountedSoFar();
        }

        @Override
        public int countedTotal() {
            return resultsCountedTotal();
        }

        @Override
        public boolean done() {
            return doneProcessingAndCounting();
        }

        @Override
        public MaxStats maxStats() {
            return (Results.this instanceof Hits ? ((Hits)Results.this).maxStats() : MaxStats.NOT_EXCEEDED);
        }

        @Override
        public String toString() {
            return "ResultsStats(" + Results.this.toString() + ")";
        }
    };

    public Results(QueryInfo queryInfo) {
        this.queryInfo = queryInfo;
//        queryInfo.ensureResultsObjectIdSet(hitsObjId); // if we're the original query, set the id.
        threadAborter = ThreadAborter.create();
    }

    /**
     * Get information about the original query.
     *
     * This includes the index, field, max. settings, and max. stats
     * (whether the max. settings were reached).
     *
     * @return query info
     */
    public QueryInfo queryInfo() {
        return queryInfo;
    }

    /**
     * Get the field these hits are from.
     *
     * @return field
     */
    public AnnotatedField field() {
        return queryInfo().field();
    }

    /**
     * Get the index these hits are from.
     *
     * @return index
     */
    public BlackLabIndex index() {
        return queryInfo().index();
    }

    public int resultsObjId() {
        return hitsObjId;
    }

    public ThreadAborter threadAborter() {
        return threadAborter;
    }

    /**
     * Is this a hits window?
     *
     * @return true if it's a window, false if not
     */
    public boolean isWindow() {
        return windowStats() != null;
    }

    /**
     * If this is a hits window, return the window stats.
     *
     * @return window stats, or null if this is not a hits window
     */
    public WindowStats windowStats() {
        return null;
    }

    /**
     * Is this sampled from another instance?
     *
     * @return true if it's a sample, false if not
     */
    public boolean isSample() {
        return sampleParameters() != null;
    }

    /**
     * If this is a sample, return the sample parameters.
     *
     * Also includes the explicitly set or randomly chosen seed.
     *
     * @return sample parameters, or null if this is not a sample
     */
    public SampleParameters sampleParameters() {
        return null;
    }

    /**
     * Return a stream of these hits.
     *
     * @return stream
     */
    public Stream<T> stream() {
        return StreamSupport.stream(this.spliterator(), false);
    }

    /**
     * Return a parallel stream of these hits.
     *
     * @return stream
     */
    public Stream<T> parallelStream() {
        return StreamSupport.stream(this.spliterator(), true);
    }



    /**
     * Return the specified hit.
     * Implementations of this method should be thread-safe.
     *
     * @param i index of the desired hit
     * @return the hit, or null if it's beyond the last hit
     */
    public abstract T get(int i);

    /**
     * Group these hits by a criterium (or several criteria).
     *
     * @param criteria the hit property to group on
     * @param maxResultsToStorePerGroup maximum number of results to store per group, or -1 for all
     * @return a HitGroups object representing the grouped hits
     */
    public abstract ResultGroups<T> group(P criteria, int maxResultsToStorePerGroup);

    /**
     * Select only the results where the specified property has the specified value.
     *
     * @param property property to select on, e.g. "word left of hit"
     * @param value value to select on, e.g. 'the'
     * @return filtered hits
     */
    public abstract Results<T, P> filter(P property, PropertyValue value);

    /**
     * Return a new Results object with these results sorted by the given property.
     *
     * This keeps the existing sort (or lack of one) intact and allows you to cache
     * different sorts of the same resultset. The result objects are reused between
     * the two Results instances, so not too much additional memory is used.
     *
     * @param sortProp the property to sort on
     * @return a new Results object with the same results, sorted in the specified way
     */
    public abstract Results<T, P> sort(P sortProp);

    /**
     * Take a sample of results.
     *
     * @param sampleParameters sample parameters
     * @return the sample
     */
    public abstract Results<T, P> sample(SampleParameters sampleParameters);

    /**
     * Get a window into this list of results.
     *
     * Use this if you're displaying part of the resultset, like in a paging
     * interface. It makes sure BlackLab only works with the results you want to
     * display and doesn't do any unnecessary processing on the other hits.
     *
     * The resulting instance will has "window stats" to assist with paging,
     * like figuring out if there hits before or after the window.
     *
     * @param first first result in the window (0-based)
     * @param windowSize desired size of the window (if there's enough results)
     * @return the window
     */
    public abstract Results<T, P> window(int first, int windowSize);

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(#" + hitsObjId + ")";
    }

    /**
     * Ensure that we have read at least as many results as specified in the parameter.
     *
     * @param number the minimum number of results that will have been read when this
     *            method returns (unless there are fewer hits than this); if
     *            negative, reads all hits
     */
    protected abstract void ensureResultsRead(int number);

    /**
     * Ensure that we have read all results.
     */
    protected void ensureAllResultsRead() {
        ensureResultsRead(-1);
    }

    public ResultsStats resultsStats() {
        return resultsStats;
    }

    protected abstract boolean resultsProcessedAtLeast(int lowerBound);

    /**
     * This is an alias of resultsProcessedTotal().
     *
     * @return number of hits processed total
     */
    public int size() {
        return resultsProcessedTotal();
    }

    protected abstract int resultsProcessedTotal();

    protected abstract int resultsProcessedSoFar();

    protected int resultsCountedSoFar() {
        return resultsProcessedSoFar();
    }

    protected int resultsCountedTotal() {
        return resultsProcessedTotal();
    }

    /**
     * Check if we're done retrieving/counting hits.
     *
     * If you're retrieving hits in a background thread, call this method from
     * another thread to check if all hits have been processed.
     *
     * @return true iff all hits have been retrieved/counted.
     */
    public abstract boolean doneProcessingAndCounting();
}
