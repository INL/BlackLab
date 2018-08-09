package nl.inl.blacklab.search.results;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.Prioritizable;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.util.ThreadPriority;

public abstract class HitsAbstract implements Iterable<Hit>, Prioritizable {

    protected static final Logger logger = LogManager.getLogger(HitsAbstract.class);

    public abstract boolean maxHitsCounted();

    public abstract boolean maxHitsRetrieved();

    public abstract boolean doneFetchingHits();

    public abstract int countSoFarDocsRetrieved();

    public abstract int countSoFarDocsCounted();

    public abstract int countSoFarHitsRetrieved();

    public abstract int countSoFarHitsCounted();

    public abstract int totalNumberOfDocs();

    public abstract int numberOfDocs();

    public abstract int size();

    public abstract int totalSize();

    public abstract boolean sizeAtLeast(int lowerBound);

    // Stats about hits fetching
    // --------------------------------------------------------------------------
    
    private ResultsNumber resultsNumberHitsProcessed = new ResultsNumber() {
        @Override
        public int total() {
            return size();
        }

        @Override
        public boolean atLeast(int lowerBound) {
            return sizeAtLeast(lowerBound);
        }

        @Override
        public int soFar() {
            return countSoFarHitsRetrieved();
        }

        @Override
        public boolean done() {
            return doneFetchingHits();
        }

        @Override
        public boolean exceededMaximum() {
            return maxHitsRetrieved();
        }

        @Override
        public int maximum() {
            return settings.maxHitsToRetrieve();
        }
    };
    
    ResultsNumber resultsNumberHitsCounted = new ResultsNumber() {
        @Override
        public int total() {
            return totalSize();
        }

        @Override
        public boolean atLeast(int lowerBound) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int soFar() {
            return countSoFarHitsCounted();
        }

        @Override
        public boolean done() {
            return doneFetchingHits();
        }

        @Override
        public boolean exceededMaximum() {
            return maxHitsCounted();
        }

        @Override
        public int maximum() {
            return settings.maxHitsToCount();
        }
    };
    
    private ResultsNumber resultsNumberDocsProcessed = new ResultsNumber() {
        @Override
        public int total() {
            return numberOfDocs();
        }

        @Override
        public boolean atLeast(int lowerBound) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int soFar() {
            return countSoFarDocsRetrieved();
        }

        @Override
        public boolean done() {
            return doneFetchingHits();
        }

        @Override
        public boolean exceededMaximum() {
            return maxHitsRetrieved();
        }

        @Override
        public int maximum() {
            throw new UnsupportedOperationException();
        }
    };
    
    private ResultsNumber resultsNumberDocsCounted = new ResultsNumber() {
        @Override
        public int total() {
            if (done())
                return soFar();
            throw new UnsupportedOperationException("Cannot return total docs until all hits have been fetched");
        }

        @Override
        public boolean atLeast(int lowerBound) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int soFar() {
            return countSoFarDocsCounted();
        }

        @Override
        public boolean done() {
            return doneFetchingHits();
        }

        @Override
        public boolean exceededMaximum() {
            return maxHitsCounted();
        }

        @Override
        public int maximum() {
            throw new UnsupportedOperationException();
        }
    };
    
    private ResultsStats hitStats = new ResultsStats() {
        @Override
        public ResultsNumber processed() {
            return resultsNumberHitsProcessed;
        }

        @Override
        public ResultsNumber counted() {
            return resultsNumberHitsCounted;
        }
    };
    
    private ResultsStats docStats = new ResultsStats() {
        @Override
        public ResultsNumber processed() {
            return resultsNumberDocsProcessed;
        }

        @Override
        public ResultsNumber counted() {
            return resultsNumberDocsCounted;
        }
    };
    
    private ResultsStatsHitsDocs hitsDocsStats = new ResultsStatsHitsDocs() {
        @Override
        public ResultsStats hits() {
            return hitStats;
        }

        @Override
        public ResultsStats docs() {
            return docStats;
        }
        
    };
    
    public ResultsStatsHitsDocs stats() {
        return hitsDocsStats;
    }
    
    protected abstract void ensureHitsRead(int number) throws InterruptedException;

    protected abstract void ensureAllHitsRead() throws InterruptedException;

    public abstract Map<String, Span> getCapturedGroupMap(Hit hit);

    public abstract Span[] getCapturedGroups(Hit hit);

    public abstract boolean hasCapturedGroups();

    public abstract List<String> getCapturedGroupNames();

    public abstract HitsAbstract getHitsInDoc(int docid);

    public abstract Hit get(int i);

    public abstract Hit getByOriginalOrder(int i);

    @Override
    public abstract Iterator<Hit> iterator();

    @Override
    public abstract String toString();

    public abstract HitsWindow window(Hit hit);

    public abstract HitsWindow window(int first, int windowSize, HitsSettings settings);

    public abstract HitsWindow window(int first, int windowSize);

    public abstract TermFrequencyList getCollocations(Annotation annotation, QueryExecutionContext ctx, boolean sort);

    public abstract TermFrequencyList getCollocations();

    public abstract DocResults perDocResults();

    public abstract HitGroups groupedBy(final HitProperty criteria);

    public abstract HitsAbstract filteredBy(HitProperty property, HitPropValue value);

    public abstract HitsAbstract sortedBy(HitProperty sortProp, boolean reverseSort);

    public abstract HitsAbstract sortedBy(final HitProperty sortProp);

    public abstract void copyMaxHitsRetrieved(HitsAbstract copyFrom);

    public abstract HitsAbstract copy(HitsSettings settings);

    /** Id the next Hits instance will get */
    private static int nextHitsObjId = 0;

    private static synchronized int getNextHitsObjId() {
        return nextHitsObjId++;
    }

    /** Unique id of this Hits instance */
    protected final int hitsObjId = getNextHitsObjId();
    protected BlackLabIndex index;

    public int getHitsObjId() {
        return hitsObjId;
    }

    /**
     * Settings for retrieving hits.
     */
    protected HitsSettings settings;
    /**
     * The field these hits came from (will also be used as concordance field)
     */
    protected AnnotatedField field;
    /**
     * Helper object for implementing query thread priority (making sure queries
     * don't hog the CPU for way too long).
     */
    protected ThreadPriority threadPriority;

    /**
     * Set the thread priority level for this Hits object.
     *
     * Allows us to set a query to low-priority, or to (almost) pause it.
     *
     * @param level the desired priority level
     */
    @Override
    public void setPriorityLevel(ThreadPriority.Level level) {
        threadPriority.setPriorityLevel(level);
    }

    /**
     * Get the thread priority level for this Hits object.
     *
     * Can be normal, low-priority, or (almost) paused.
     *
     * @return the current priority level
     */
    @Override
    public ThreadPriority.Level getPriorityLevel() {
        return threadPriority.getPriorityLevel();
    }

    /**
     * Returns the searcher object.
     *
     * @return the searcher object.
     */
    public BlackLabIndex index() {
        return index;
    }

    public AnnotatedField field() {
        return field;
    }

    public HitsSettings settings() {
        return settings;
    }

    public ThreadPriority getThreadPriority() {
        return threadPriority;
    }

    public Concordances concordances(int contextSize) {
        return new Concordances(this, contextSize);
    }

    public Kwics kwics(int contextSize) {
        return new Kwics(this, contextSize);
    }

}
