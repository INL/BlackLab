package nl.inl.blacklab.search.results;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.util.ThreadPauser;

public abstract class HitsAbstract implements Hits {

    protected static final Logger logger = LogManager.getLogger(HitsAbstract.class);

    /** Id the next Hits instance will get */
    private static int nextHitsObjId = 0;

    private static synchronized int getNextHitsObjId() {
        return nextHitsObjId++;
    }

    /** Unique id of this Hits instance */
    protected final int hitsObjId = getNextHitsObjId();
    
    protected BlackLabIndex index;

    /**
     * Settings for retrieving hits.
     */
    protected HitsSettings settings;
    /**
     * The field these hits came from (will also be used as concordance field)
     */
    protected AnnotatedField field;
    /**
     * Helper object for pausing threads (making sure queries
     * don't hog the CPU for way too long).
     */
    protected ThreadPauser threadPauser;

    public HitsAbstract(BlackLabIndex index, AnnotatedField field, HitsSettings settings) {
        this.index = index;
        this.field = field;
        this.settings = settings == null ? index.hitsSettings() : settings;
        threadPauser = new ThreadPauser();
    }

    @Override
    public abstract int hitsProcessedSoFar();

    @Override
    public abstract boolean hitsProcessedAtLeast(int lowerBound);

    @Override
    public abstract int hitsProcessedTotal();

    @Override
    public abstract int hitsCountedSoFar();

    @Override
    public abstract int hitsCountedTotal();

    @Override
    public abstract int docsProcessedSoFar();

    @Override
    public abstract int docsProcessedTotal();

    @Override
    public abstract int docsCountedSoFar();

    @Override
    public abstract int docsCountedTotal();

    @Override
    public abstract boolean doneProcessingAndCounting();

    @Override
    public abstract boolean hitsCountedExceededMaximum();

    @Override
    public abstract boolean hitsProcessedExceededMaximum();

    @Override
    public int size() {
        return hitsProcessedTotal();
    }

    @Override
    public abstract Map<String, Span> capturedGroupMap(Hit hit);

    @Override
    public abstract Span[] capturedGroups(Hit hit);

    @Override
    public abstract boolean hasCapturedGroups();

    @Override
    public abstract List<String> capturedGroupNames();

    @Override
    public abstract Hits getHitsInDoc(int docid);

    @Override
    public abstract Hit get(int i);

    @Override
    public abstract Hit getByOriginalOrder(int i);

    @Override
    public abstract Iterator<Hit> iterator();

    @Override
    public abstract String toString();

    @Override
    public abstract HitsWindow window(Hit hit);

    @Override
    public abstract HitsWindow window(int first, int windowSize, HitsSettings settings);

    @Override
    public abstract HitsWindow window(int first, int windowSize);

    @Override
    public abstract TermFrequencyList collocations(Annotation annotation, QueryExecutionContext ctx, boolean sort);

    @Override
    public abstract TermFrequencyList collocations();

    @Override
    public abstract DocResults perDocResults();

    @Override
    public abstract HitGroups groupedBy(final HitProperty criteria);

    @Override
    public abstract Hits filteredBy(HitProperty property, HitPropValue value);

    @Override
    public abstract Hits sortedBy(HitProperty sortProp, boolean reverseSort);

    @Override
    public abstract Hits sortedBy(final HitProperty sortProp);

    @Override
    public abstract void copyMaxHitsRetrieved(Hits copyFrom);

    @Override
    public abstract HitsAbstract copy(HitsSettings settings);

    @Override
    public int resultsObjId() {
        return hitsObjId;
    }

    @Override
    public void pause(boolean pause) {
        threadPauser.pause(pause);
    }

    @Override
    public boolean isPaused() {
        return threadPauser.isPaused();
    }

    @Override
    public BlackLabIndex index() {
        return index;
    }

    @Override
    public AnnotatedField field() {
        return field;
    }

    @Override
    public HitsSettings settings() {
        return settings;
    }
    
    @Override
    public ThreadPauser threadPauser() {
        return threadPauser;
    }

    @Override
    public Concordances concordances(int contextSize) {
        return new Concordances(this, contextSize);
    }

    @Override
    public Kwics kwics(int contextSize) {
        return new Kwics(this, contextSize);
    }

}
