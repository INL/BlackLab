package nl.inl.blacklab.search.results;

import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import it.unimi.dsi.fastutil.ints.IntArrays;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.forwardindex.FiidLookup;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.util.Sort;
import nl.inl.util.Sort.Sortable;

public abstract class Hits extends Results<Hit, HitProperty> {
    public static class EphemeralHit implements Hit {
        public int doc = -1;
        public int start = -1;
        public int end = -1;

        public HitImpl toHit() {
            return new HitImpl(doc, start, end);
        }

        @Override
        public int doc() {
            return doc;
        }

        @Override
        public int start() {
            return start;
        }

        @Override
        public int end() {
            return end;
        }
    }

    public static class HitsArrays implements Iterable<EphemeralHit> {
        @FunctionalInterface
        public static interface HitConsumer {
            public void consume(int doc, int start, int end);
        }

        public static class HitIterator implements Iterator<EphemeralHit> {
            private HitsArrays hits;
            private int pos = 0;
            private final EphemeralHit hit = new EphemeralHit();

            private HitIterator(HitsArrays h) {
                this.hits = h;
            }

            @Override
            public boolean hasNext() {
                // Since this iteration method is not thread-safe anyway, use the direct array to prevent repeatedly acquiring the read lock
                return this.hits.docs.size() > this.pos;
            }

            @Override
            public EphemeralHit next() {
                this.hit.doc = this.hits.docs.get(pos);
                this.hit.start = this.hits.starts.get(pos);
                this.hit.end = this.hits.ends.get(pos);
                ++this.pos;
                return this.hit;
            }

            public int doc() {
                return this.hit.doc;
            }

            public int start() {
                return this.hit.start;
            }

            public int end() {
                return this.hit.end;
            }
        }

        private ReadWriteLock lock = new ReentrantReadWriteLock();

        private final IntArrayList docs;
        private final IntArrayList starts;
        private final IntArrayList ends;

        public HitsArrays() {
            this.docs = new IntArrayList();
            this.starts = new IntArrayList();
            this.ends = new IntArrayList();
        }

        public HitsArrays(IntArrayList docs, IntArrayList starts, IntArrayList ends) {
            if (docs == null || starts == null || ends == null)
                throw new NullPointerException();
            if (docs.size() != starts.size() || docs.size() != ends.size())
                throw new IllegalArgumentException("Passed differently sized hit component arrays to Hits object");

            this.docs = docs;
            this.starts = starts;
            this.ends = ends;
        }

        public void add(int doc, int start, int end) {
            this.lock.writeLock().lock();
            docs.add(doc);
            starts.add(start);
            ends.add(end);
            this.lock.writeLock().unlock();
        }

        public void addAll(IntArrayList docs, IntArrayList starts, IntArrayList ends) {
            this.lock.writeLock().lock();
            this.docs.addAll(docs);
            this.starts.addAll(starts);
            this.ends.addAll(ends);
            this.lock.writeLock().unlock();
        }

        /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
        public void add(EphemeralHit hit) {
            this.lock.writeLock().lock();
            docs.add(hit.doc);
            starts.add(hit.start);
            ends.add(hit.end);
            this.lock.writeLock().unlock();
        }

        /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
        public void add(Hit hit) {
            this.lock.writeLock().lock();
            docs.add(hit.doc());
            starts.add(hit.start());
            ends.add(hit.end());
            this.lock.writeLock().unlock();
        }

        public void addAll(List<Hit> hits) {
            this.lock.writeLock().lock();
            for (Hit hit : hits) {
                docs.add(hit.doc());
                starts.add(hit.start());
                ends.add(hit.end());
            }
            this.lock.writeLock().unlock();
        }

        public void addAll(HitsArrays hits) {
            this.lock.writeLock().lock();
            hits.lock.readLock().lock();
            docs.addAll(hits.docs());
            starts.addAll(hits.starts());
            ends.addAll(hits.ends());
            hits.lock.readLock().unlock();
            this.lock.writeLock().unlock();
        }

        public void withReadLock(Consumer<HitsArrays> cons) {
            lock.readLock().lock();
            cons.accept(this);
            lock.readLock().unlock();
        }

        public void withWriteLock(Consumer<HitsArrays> cons) {
            lock.writeLock().lock();
            cons.accept(this);
            lock.writeLock().unlock();
        }

        public void use(int index, HitConsumer cons) {
            lock.readLock().lock();
            cons.consume(docs.get(index), starts.get(index), ends.get(index));
            lock.readLock().unlock();
        }

        public HitImpl get(int index) {
            lock.readLock().lock();
            HitImpl h = new HitImpl(docs.get(index), starts.get(index), ends.get(index));
            lock.readLock().unlock();
            return h;
        }

        /**
         * Copy values into the ephemeral hit, for use in a hot loop or somesuch.
         * The intent of this function is to allow retrieving many hits without needing to allocate so many short lived objects.
         * Example:
         *
         * <pre>
         * EphemeralHitImpl h = new EphemeralHitImpl();
         * int size = hits.size();
         * for (int i = 0; i < size; ++i) {
         *     hits.getEphemeral(i, h);
         *     // use h now
         * }
         * </pre>
         */
        public void getEphemeral(int index, EphemeralHit h) {
            lock.readLock().lock();
            h.doc = docs.get(index);
            h.start = starts.get(index);
            h.end = ends.get(index);
            lock.readLock().unlock();
        }

        public int doc(int index) {
            lock.readLock().lock();
            int doc = this.docs.get(index);
            lock.readLock().unlock();
            return doc;
        }

        public int start(int index) {
            lock.readLock().lock();
            int start = this.starts.get(index);
            lock.readLock().unlock();
            return start;
        }

        public int end(int index) {
            lock.readLock().lock();
            int end = this.ends.get(index);
            lock.readLock().unlock();
            return end;
        }

        public int size() {
            lock.readLock().lock();
            int size = docs.size();
            lock.readLock().unlock();
            return size;
        }

        /**
         * Expert use: get the internal docs array.
         * The array is not locked, so care should be taken when reading it.
         * Best to wrap usage of this function and the returned in a withReadLock call.
         *
         * @return
         */
        public IntArrayList docs() {
            return docs;
        }

        /**
         * Expert use: get the internal starts array.
         * The array is not locked, so care should be taken when reading it.
         * Best to wrap usage of this function and the returned in a withReadLock call.
         *
         * @return
         */
        public IntArrayList starts() {
            return starts;
        }

        /**
         * Expert use: get the internal ends array.
         * The array is not locked, so care should be taken when reading it.
         * Best to wrap usage of this function and the returned in a withReadLock call.
         *
         * @return
         */
        public IntArrayList ends() {
            return ends;
        }

        /** Note: iterating does not lock the arrays, to do that, it should be performed in a {@link #withReadLock} callback. */
        @Override
        public HitIterator iterator() {
            return new HitIterator(this);
        }

        /**
         * Sort this instance. If you want a copy of the data anyway, it's better to use regular sort()
         * Note this will be less efficient than regular sort(), because this avoids allocating copies of internal data, thus needing more swaps.
         * In order to do that, more swaps and pointer chases are required (in practice, 0.5n pointer chases of average length log2(n), and 0.5n
         * swaps)
         */
        public void sortInPlace(HitProperty p) {
            this.lock.writeLock().lock();

            final EphemeralHit tmp = new EphemeralHit();
            Sort.sort(new Sortable() {
                @Override
                public void swap(int a, int b) {
                    getEphemeral(a, tmp);

                    docs.set(a, docs.get(b));
                    starts.set(a, starts.get(b));
                    ends.set(a, ends.get(b));

                    docs.set(b, tmp.doc);
                    starts.set(b, tmp.start);
                    ends.set(b, tmp.end);
                }

                @Override
                public int compare(int a, int b) {
                    return p.compare(a, b);
                }
            }, 0, this.size());
        }

        public HitsArrays sort(HitProperty p) {
            this.lock.readLock().lock();

            int[] indices = new int[this.size()];
            for (int i = 0; i < indices.length; ++i)
                indices[i] = i;

            IntArrays.quickSort(indices, p::compare);

            HitsArrays r = new HitsArrays();
            EphemeralHit eph = new EphemeralHit();
            for (int i = 0; i < indices.length; ++i) {
                getEphemeral(indices[i], eph);
                r.add(eph);
            }
            this.lock.readLock().unlock();
            return r;
        }
    }

    private static final HitsArrays EMPTY_SINGLETON = new HitsArrays() {
        @Override
        public void add(EphemeralHit hit) {throw new BlackLabRuntimeException("Attempting to write into empty Hits object"); };
        @Override
        public void add(Hit hit) {throw new BlackLabRuntimeException("Attempting to write into empty Hits object"); };
        @Override
        public void add(int doc, int start, int end){throw new BlackLabRuntimeException("Attempting to write into empty Hits object"); };
        @Override
        public void addAll(HitsArrays hits) {throw new BlackLabRuntimeException("Attempting to write into empty Hits object"); };
        @Override
        public void addAll(IntArrayList docs, IntArrayList starts, IntArrayList ends) {throw new BlackLabRuntimeException("Attempting to write into empty Hits object"); };
        @Override
        public void addAll(java.util.List<Hit> hits) {throw new BlackLabRuntimeException("Attempting to write into empty Hits object"); };
    };


    protected final HitsArrays hitsArrays;

    protected static final Logger logger = LogManager.getLogger(Hits.class);

    /**
     * Construct a Hits object from a SpanQuery.
     *
     * @param queryInfo information about the original query
     * @param query the query to execute to get the hits
     * @param searchSettings settings such as max. hits to process/count
     * @return hits found
     * @throws WildcardTermTooBroad if a wildcard term matches too many terms in the index
     */
    public static Hits fromSpanQuery(QueryInfo queryInfo, BLSpanQuery query, SearchSettings searchSettings) throws WildcardTermTooBroad {
        if (queryInfo.index().blackLab().maxThreadsPerSearch() <= 1) {
            // We don't want to use multi-threaded search. Stick with the single-threaded version.
            return new HitsFromQuery(queryInfo, query, searchSettings);
        } else {
            return new HitsFromQueryParallel(queryInfo, query, searchSettings);
        }
    }

    /**
     * Make a wrapper Hits object for a list of Hit objects.
     *
     * Will create Hit objects from the arrays. Mainly useful for testing.
     * Prefer using @link { {@link #fromArrays(QueryInfo, IntArrayList, IntArrayList, IntArrayList) }
     *
     * @param queryInfo information about the original query
     * @param doc doc ids
     * @param start hit starts
     * @param end hit ends
     * @return hits found
     */
    public static Hits fromArrays(QueryInfo queryInfo, int[] docs, int[] starts, int[] ends) {
        return new HitsList(queryInfo, new HitsArrays(new IntArrayList(docs), new IntArrayList(starts), new IntArrayList(ends)), null);
    }

    public static Hits fromList(QueryInfo queryInfo, HitsArrays hits, CapturedGroups capturedGroups) {
        return new HitsList(queryInfo, hits, capturedGroups);
    }

    public static Hits fromList(
                                QueryInfo queryInfo,
                                HitsArrays hits,
                                WindowStats windowStats,
                                SampleParameters sampleParameters,
                                int hitsCounted,
                                int docsRetrieved,
                                int docsCounted,
                                CapturedGroups capturedGroups) {
        return new HitsList(
                            queryInfo,
                            hits,
                            windowStats,
                            sampleParameters,
                            hitsCounted,
                            docsRetrieved,
                            docsCounted,
                            capturedGroups);
    }

    /**
     * Construct an empty Hits object.
     *
     * @param queryInfo query info
     * @return hits found
     *
     * @deprecated use {@link #immutableEmptyList(QueryInfo)} or {@link #mutableEmptyList(QueryInfo)}.
     */
    @Deprecated
    public static Hits emptyList(QueryInfo queryInfo) {
        return new HitsList(queryInfo, null, null);
    }

    /**
     * Construct an immutable empty Hits object.
     *
     * @param queryInfo query info
     * @return hits found
     */
    public static Hits immutableEmptyList(QueryInfo queryInfo) {
        return new HitsList(queryInfo, EMPTY_SINGLETON, null);
    }

    /**
     * Construct a mutable empty Hits object.
     *
     * @param queryInfo query info
     * @return hits found
     */
    public static Hits mutableEmptyList(QueryInfo queryInfo) {
        return new HitsList(queryInfo, new HitsArrays(), null);
    }

    /**
     * Minimum number of hits to fetch in an ensureHitsRead() block.
     *
     * This prevents locking again and again for a single hit when iterating.
     *
     * See {@link HitsFromQuery} and {@link HitsFiltered}.
     */
    protected static final int FETCH_HITS_MIN = 20;

    /**
     * Our captured groups, or null if we have none.
     */
    protected CapturedGroups capturedGroups;

    /**
     * The number of hits we've seen and counted so far. May be more than the number
     * of hits we've retrieved if that exceeds maxHitsToRetrieve.
     */
    protected int hitsCounted = 0;

    /**
     * The number of separate documents we've seen in the hits retrieved.
     */
    protected int docsRetrieved = 0;

    /**
     * The number of separate documents we've counted so far (includes non-retrieved
     * hits).
     */
    protected int docsCounted = 0;

    private ResultsStats docsStats = new ResultsStats() {

        @Override
        public boolean processedAtLeast(int lowerBound) {
            while (!doneProcessingAndCounting() && docsProcessedSoFar() < lowerBound) {
                ensureResultsRead(hitsArrays.size() + FETCH_HITS_MIN);
            }
            return docsProcessedSoFar() >= lowerBound;
        }

        @Override
        public int processedTotal() {
            return docsProcessedTotal();
        }

        @Override
        public int processedSoFar() {
            return docsProcessedSoFar();
        }

        @Override
        public int countedSoFar() {
            return docsCountedSoFar();
        }

        @Override
        public int countedTotal() {
            return docsCountedTotal();
        }

        @Override
        public boolean done() {
            return doneProcessingAndCounting();
        }

        @Override
        public MaxStats maxStats() {
            return Hits.this.maxStats();
        }

        @Override
        public String toString() {
            return "ResultsStats(" + Hits.this.toString() + ")";
        }

    };

    /** Construct an empty, mutable Hits object.
     *
     * @param queryInfo query info for corresponding query
     * @deprecated if you need an empty Hits object, use {@link #Hits(QueryInfo, boolean)}; otherwise, use {@link #Hits(QueryInfo, HitsArrays)}
     */
    @Deprecated
    public Hits(QueryInfo queryInfo) {
        this(queryInfo, false);
    }

    /** Construct an empty Hits object.
     *
     * @param queryInfo query info for corresponding query
     * @param readOnly if true, returns an immutable Hits object; otherwise, a mutable one
     */
    public Hits(QueryInfo queryInfo, boolean readOnly) {
        this(queryInfo, readOnly ? EMPTY_SINGLETON : new HitsArrays());
    }

    /**
     * Construct a Hits object from a hits array.
     *
     * NOTE: if you pass null, a new, mutable HitsArray is used. For an immutable empty Hits object, use {@link #Hits(QueryInfo, boolean)}.
     *
     * @param queryInfo query info for corresponding query
     * @param hits hits array to use for this object. The array is used as-is, not copied.
     */
    public Hits(QueryInfo queryInfo, HitsArrays hits) {
        super(queryInfo);
        this.hitsArrays = hits == null ? new HitsArrays() : hits;
    }

    // Inherited from Results
    //--------------------------------------------------------------------

    /**
     * Get a window into this list of hits.
     *
     * Use this if you're displaying part of the resultset, like in a paging
     * interface. It makes sure BlackLab only works with the hits you want to
     * display and doesn't do any unnecessary processing on the other hits.
     *
     * HitsWindow includes methods to assist with paging, like figuring out if there
     * hits before or after the window.
     *
     * @param first first hit in the window (0-based)
     * @param windowSize size of the window
     * @return the window
     */
    @Override
    public Hits window(int first, int windowSize) {
        // Error if first out of range
        WindowStats windowStats;
        boolean emptyResultSet = !hitsProcessedAtLeast(1);
        if (first < 0 || (emptyResultSet && first > 0) ||
            (!emptyResultSet && !hitsProcessedAtLeast(first + 1))) {
            //throw new IllegalArgumentException("First hit out of range");
            return Hits.immutableEmptyList(queryInfo());
        }

        // Auto-clamp number
        // take care not to always call size(), as that blocks until we're done!
        // Instead, first call ensureResultsRead so we block until we have either have enough or finish
        this.ensureResultsRead(first + windowSize);
        // and only THEN do this, since now we know if we don't have this many hits, we're done, and it's safe to call size
        int number = hitsProcessedAtLeast(first + windowSize) ? windowSize : size() - first;

        // Copy the hits we're interested in.
        CapturedGroups capturedGroups = hasCapturedGroups() ? new CapturedGroupsImpl(capturedGroups().names()) : null;
        MutableInt docsRetrieved = new MutableInt(0); // Bypass warning (enclosing scope must be effectively final)
        HitsArrays window = new HitsArrays();

        this.hitsArrays.withReadLock(h -> {
            int prevDoc = -1;
            EphemeralHit hit = new EphemeralHit();
            for (int i = first; i < first + number; i++) {
                h.getEphemeral(i, hit);
                if (capturedGroups != null) {
                    Hit hh = hit.toHit();
                    capturedGroups.put(hh, capturedGroups().get(hh));
                }
                // OPT: copy context as well..?

                int doc = hit.doc;
                if (doc != prevDoc) {
                    docsRetrieved.add(1);
                    prevDoc = doc;
                }
                window.add(hit);
            }
        });
        boolean hasNext = hitsProcessedAtLeast(first + windowSize + 1);
        windowStats = new WindowStats(hasNext, first, windowSize, number);
        return Hits.fromList(queryInfo(), window, windowStats, null, hitsCounted, docsRetrieved.getValue(), docsRetrieved.getValue(), capturedGroups);
    }

    /**
     * Take a sample of hits by wrapping an existing Hits object.
     *
     * @param sampleParameters sample parameters
     * @return the sample
     */
    @Override
    public Hits sample(SampleParameters sampleParameters) {
        // We can later provide an optimized version that uses a HitsSampleCopy or somesuch
        // (this class could save memory by only storing the hits we're interested in)
        Random random = new Random(sampleParameters.seed());
        Set<Integer> chosenHitIndices = new TreeSet<>();
        int numberOfHitsToSelect = sampleParameters.numberOfHits(size());
        if (numberOfHitsToSelect > size()) {
            numberOfHitsToSelect = size(); // default to all hits in this case
            for (int i = 0; i < numberOfHitsToSelect; ++i) {
                chosenHitIndices.add(i);
            }
        } else {
            // Choose the hits
            for (int i = 0; i < numberOfHitsToSelect; i++) {
                // Choose a hit we haven't chosen yet
                int hitIndex;
                do {
                    hitIndex = random.nextInt(size());
                } while (chosenHitIndices.contains(hitIndex));
                chosenHitIndices.add(hitIndex);
            }
        }

        MutableInt docsInSample = new MutableInt(0);
        CapturedGroups capturedGroups = hasCapturedGroups() ? new CapturedGroupsImpl(capturedGroups().names()) : null;
        HitsArrays sample = new HitsArrays();

        this.hitsArrays.withReadLock(__ -> {
            int previousDoc = -1;
            EphemeralHit hit = new EphemeralHit();
            for (Integer hitIndex : chosenHitIndices) {
                this.hitsArrays.getEphemeral(hitIndex, hit);
                if (hit.doc != previousDoc) {
                    docsInSample.add(1);
                    previousDoc = hit.doc;
                }

                sample.add(hit);
                if (capturedGroups != null) {
                    Hit h = hit.toHit();
                    capturedGroups.put(h, this.capturedGroups.get(h));
                }
            }
        });

        return Hits.fromList(queryInfo(), sample, null, sampleParameters, sample.size(), docsInSample.getValue(), docsInSample.getValue(), capturedGroups);
    }

    /**
     * Return a new Hits object with these hits sorted by the given property.
     *
     * This keeps the existing sort (or lack of one) intact and allows you to cache
     * different sorts of the same resultset.
     *
     * @param sortProp the hit property to sort on
     * @return a new Hits object with the same hits, sorted in the specified way
     */
    @Override
    public Hits sort(HitProperty sortProp) {
        // We need a HitProperty with the correct Hits object
        // If we need context, make sure we have it.
        List<Annotation> requiredContext = sortProp.needsContext();
        List<FiidLookup> fiidLookups = FiidLookup.getList(requiredContext, queryInfo().index().reader());
        sortProp = sortProp.copyWith(this,
            requiredContext == null ? null : new Contexts(this, requiredContext, sortProp.needsContextSize(index()), fiidLookups));

        // Perform the actual sort.
        this.ensureAllResultsRead();
        HitsArrays sorted = this.hitsArrays.sort(sortProp); // TODO use wrapper objects

        CapturedGroups capturedGroups = capturedGroups();
        int hitsCounted = hitsCountedSoFar();
        int docsRetrieved = docsProcessedSoFar();
        int docsCounted = docsCountedSoFar();
        return Hits.fromList(queryInfo(), sorted, null, null, hitsCounted, docsRetrieved, docsCounted, capturedGroups);
    }

    @Override
    public HitGroups group(HitProperty criteria, int maxResultsToStorePerGroup) {
        ensureAllResultsRead();
        return HitGroups.fromHits(this, criteria, maxResultsToStorePerGroup);
    }

    /**
     * Select only the hits where the specified property has the specified value.
     *
     * @param property property to select on, e.g. "word left of hit"
     * @param value value to select on, e.g. 'the'
     * @return filtered hits
     */
    @Override
    public Hits filter(HitProperty property, PropertyValue value) {
        return new HitsFiltered(this, property, value);
    }

    @Override
    protected int resultsCountedTotal() {
        return hitsCountedTotal();
    }

    @Override
    protected int resultsCountedSoFar() {
        return hitsCountedSoFar();
    }

    @Override
    protected boolean resultsProcessedAtLeast(int lowerBound) {
        return this.hitsArrays.size() >= lowerBound;
    }

    @Override
    protected int resultsProcessedTotal() {
        ensureResultsRead(-1);
        return this.hitsArrays.size();
    }

    @Override
    protected int resultsProcessedSoFar() {
        return hitsProcessedSoFar();
    }

    @Override
    public int numberOfResultObjects() {
        return this.hitsArrays.size();
    }

    @Override
    public Iterator<Hit> iterator() {
        // We need to wrap the internal iterator, as we probably shouldn't
        return new Iterator<Hit>() {
            Iterator<EphemeralHit> i = ephemeralIterator();

            @Override
            public boolean hasNext() {
                return i.hasNext();
            }

            @Override
            public Hit next() {
                return i.next().toHit();
            }
        };
    }

    public Iterator<EphemeralHit> ephemeralIterator() {
        ensureAllResultsRead();
        return hitsArrays.iterator();
    }

    @Override
    public Hit get(int i) {
        return this.hitsArrays.get(i);
    }

    /**
     * Did we exceed the maximum number of hits to process/count?
     *
     * NOTE: this is only valid for the original Hits instance (that
     * executes the query), and not for any derived Hits instance (window, sorted, filtered, ...).
     *
     * The reason that this is not part of QueryInfo is that this creates a brittle
     * link between derived Hits instances and the original Hits instances, which by now
     * may have been aborted, leaving the max stats in a frozen, incorrect state.
     *
     * @return our max stats, or {@link MaxStats#NOT_EXCEEDED} if not available for this instance
     */
    public abstract MaxStats maxStats();

    /**
     * Count occurrences of context words around hit.
     *
     * @param annotation what annotation to get collocations for
     * @param contextSize how many words around the hits to use
     * @param sensitivity what sensitivity to use
     * @param sort sort the resulting collocations by descending frequency?
     *
     * @return the frequency of each occurring token
     */
    public TermFrequencyList collocations(Annotation annotation, ContextSize contextSize, MatchSensitivity sensitivity, boolean sort) {
        return TermFrequencyList.collocations(this, annotation, contextSize, sensitivity, sort);
    }

    /**
     * Count occurrences of context words around hit.
     *
     * Sorts the results from most to least frequent.
     *
     * @param annotation what annotation to get collocations for
     * @param contextSize how many words around the hits to use
     * @param sensitivity what sensitivity to use
     * @return the frequency of each occurring token
     */
    public TermFrequencyList collocations(Annotation annotation, ContextSize contextSize, MatchSensitivity sensitivity) {
        return collocations(annotation, contextSize, sensitivity, true);
    }

    /**
     * Count occurrences of context words around hit.
     *
     * Matches case- and diacritics-sensitively, and sorts the results from most to least frequent.
     *
     * @param annotation what annotation to get collocations for
     * @param contextSize how many words around the hits to use
     *
     * @return the frequency of each occurring token
     */
    public TermFrequencyList collocations(Annotation annotation, ContextSize contextSize) {
        return collocations(annotation, contextSize, MatchSensitivity.SENSITIVE, true);
    }

    /**
     * Return a per-document view of these hits.
     *
     * @param maxHits
     *
     * @return the per-document view.
     */
    public DocResults perDocResults(int maxHits) {
        return DocResults.fromHits(queryInfo(), this, maxHits);
    }

    /**
     * Create concordances from the forward index.
     *
     * @param contextSize desired context size
     * @return concordances
     */
    public Concordances concordances(ContextSize contextSize) {
        return concordances(contextSize, ConcordanceType.FORWARD_INDEX);
    }

    // Getting / iterating over the hits
    //--------------------------------------------------------------------

    public Hits getHitsInDoc(int docid) {
        ensureAllResultsRead();
        HitsArrays r = new HitsArrays();
        // all hits read, no lock needed.
        for (EphemeralHit h : this.hitsArrays) {
            if (h.doc == docid)
                r.add(h);
        }
        return new HitsList(queryInfo(), r, null);
    }

    // Stats
    // ---------------------------------------------------------------

    public ResultsStats hitsStats() {
        return resultsStats();
    }

    protected boolean hitsProcessedAtLeast(int lowerBound) {
        return this.hitsArrays.size() >= lowerBound;
    }

    protected int hitsProcessedTotal() {
        ensureAllResultsRead();
        return this.hitsArrays.size();
    }

    protected int hitsProcessedSoFar() {
        return this.hitsArrays.size();
    }

    protected int hitsCountedTotal() {
        ensureAllResultsRead();
        return hitsCounted;
    }

    public ResultsStats docsStats() {
        return docsStats;
    }

    protected int docsProcessedTotal() {
        ensureAllResultsRead();
        return docsRetrieved;
    }

    protected int docsCountedTotal() {
        ensureAllResultsRead();
        return docsCounted;
    }

    protected int hitsCountedSoFar() {
        return hitsCounted;
    }

    protected int docsCountedSoFar() {
        return docsCounted;
    }

    protected int docsProcessedSoFar() {
        return docsRetrieved;
    }

    // Deriving other Hits / Results instances
    //--------------------------------------------------------------------

    /** Assumes this hit is within our lists. */
    public Hits window(Hit hit) {
        int size = this.size();

        boolean isLastHit = this.hitsArrays.get(this.hitsArrays.size() - 1).equals(hit);
        boolean hasMoreHits = isLastHit ? resultsProcessedAtLeast(size + 1) : true;

        CapturedGroups capturedGroups = null;
        if (this.capturedGroups != null) {
            capturedGroups = new CapturedGroupsImpl(this.capturedGroups.names());
            capturedGroups.put(hit, this.capturedGroups.get(hit));
        }

        HitsArrays r = new HitsArrays();
        r.add(hit);

        return Hits.fromList(
            this.queryInfo(),
            r,
            new WindowStats(hasMoreHits, 1, 1, 1),
            null, // window is not sampled
            1,
            1,
            1,
            capturedGroups);
    }

    // Captured groups
    //--------------------------------------------------------------------

    public CapturedGroups capturedGroups() {
        return capturedGroups;
    }

    public boolean hasCapturedGroups() {
        return capturedGroups != null;
    }

    // Hits display
    //--------------------------------------------------------------------

    public Concordances concordances(ContextSize contextSize, ConcordanceType type) {
        if (contextSize == null)
            contextSize = index().defaultContextSize();
        if (type == null)
            type = ConcordanceType.FORWARD_INDEX;
        return new Concordances(this, type, contextSize);
    }

    public Kwics kwics(ContextSize contextSize) {
        if (contextSize == null)
            contextSize = index().defaultContextSize();
        return new Kwics(this, contextSize);
    }

    public HitsArrays hitsArrays() {
        return hitsArrays;
    }
}
