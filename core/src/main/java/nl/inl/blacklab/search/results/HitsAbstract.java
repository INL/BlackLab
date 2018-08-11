package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.ResultNotFound;
import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.QueryExecutionContext;
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
    public abstract CapturedGroups capturedGroups();

    @Override
    public abstract boolean hasCapturedGroups();

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
    public Hits filteredBy(HitProperty property, HitPropValue value) {
        List<Annotation> requiredContext = property.needsContext();
        property.setContexts(new Contexts(this, requiredContext));

        List<Hit> filtered = new ArrayList<>();
        for (int i = 0; i < size(); i++) {
            if (property.get(i).equals(value))
                filtered.add(get(i));
        }
        HitsImpl hits = new HitsImpl(index, field, filtered, settings);
        hits.copyMaxHitsRetrieved(this);
        return hits;
    }

    @Override
    public HitGroups groupedBy(final HitProperty criteria) {
        return ResultsGrouper.fromHits(this, criteria);
    }

    @Override
    public DocResults perDocResults() {
        return DocResults.fromHits(index(), this);
    }

    @Override
    public TermFrequencyList collocations() {
        return TermFrequencyList.collocations(this, null, null, true);
    }

    @Override
    public synchronized TermFrequencyList collocations(Annotation annotation, QueryExecutionContext ctx, boolean sort) {
        return TermFrequencyList.collocations(this, annotation, ctx, sort);
    }
    
    @Override
    public HitsWindow window(int first, int windowSize) {
        return new HitsWindow(this, first, windowSize, settings());
    }

    @Override
    public HitsWindow window(int first, int windowSize, HitsSettings settings) {
        return new HitsWindow(this, first, windowSize, settings == null ? this.settings() : settings);
    }

    @Override
    public HitsWindow window(Hit hit) throws ResultNotFound {
        int i = indexOf(hit);
        if (i < 0)
            throw new ResultNotFound("Hit not found in hits list!");
        return window(i, 1);
    }

    protected abstract int indexOf(Hit hit);

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
