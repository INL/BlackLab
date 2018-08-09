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
import nl.inl.util.ThreadPriority;

public abstract class HitsAbstract implements Hits {

    protected static final Logger logger = LogManager.getLogger(HitsAbstract.class);

    public HitsAbstract(BlackLabIndex index, AnnotatedField field, HitsSettings settings) {
        this.index = index;
        this.field = field;
        this.settings = settings == null ? index.hitsSettings() : settings;
    }

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#hitsProcessedSoFar()
     */
    @Override
    public abstract int hitsProcessedSoFar();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#hitsProcessedAtLeast(int)
     */
    @Override
    public abstract boolean hitsProcessedAtLeast(int lowerBound);

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#hitsProcessedTotal()
     */
    @Override
    public abstract int hitsProcessedTotal();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#hitsCountedSoFar()
     */
    @Override
    public abstract int hitsCountedSoFar();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#hitsCountedTotal()
     */
    @Override
    public abstract int hitsCountedTotal();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#docsProcessedSoFar()
     */
    @Override
    public abstract int docsProcessedSoFar();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#docsProcessedTotal()
     */
    @Override
    public abstract int docsProcessedTotal();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#docsCountedSoFar()
     */
    @Override
    public abstract int docsCountedSoFar();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#docsCountedTotal()
     */
    @Override
    public abstract int docsCountedTotal();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#doneProcessingAndCounting()
     */
    @Override
    public abstract boolean doneProcessingAndCounting();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#hitsCountedExceededMaximum()
     */
    @Override
    public abstract boolean hitsCountedExceededMaximum();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#hitsProcessedExceededMaximum()
     */
    @Override
    public abstract boolean hitsProcessedExceededMaximum();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#size()
     */
    @Override
    public int size() {
        return hitsProcessedTotal();
    }

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#getCapturedGroupMap(nl.inl.blacklab.search.results.Hit)
     */
    @Override
    public abstract Map<String, Span> getCapturedGroupMap(Hit hit);

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#getCapturedGroups(nl.inl.blacklab.search.results.Hit)
     */
    @Override
    public abstract Span[] getCapturedGroups(Hit hit);

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#hasCapturedGroups()
     */
    @Override
    public abstract boolean hasCapturedGroups();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#getCapturedGroupNames()
     */
    @Override
    public abstract List<String> getCapturedGroupNames();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#getHitsInDoc(int)
     */
    @Override
    public abstract Hits getHitsInDoc(int docid);

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#get(int)
     */
    @Override
    public abstract Hit get(int i);

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#getByOriginalOrder(int)
     */
    @Override
    public abstract Hit getByOriginalOrder(int i);

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#iterator()
     */
    @Override
    public abstract Iterator<Hit> iterator();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#toString()
     */
    @Override
    public abstract String toString();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#window(nl.inl.blacklab.search.results.Hit)
     */
    @Override
    public abstract HitsWindow window(Hit hit);

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#window(int, int, nl.inl.blacklab.search.results.HitsSettings)
     */
    @Override
    public abstract HitsWindow window(int first, int windowSize, HitsSettings settings);

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#window(int, int)
     */
    @Override
    public abstract HitsWindow window(int first, int windowSize);

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#getCollocations(nl.inl.blacklab.search.indexmetadata.Annotation, nl.inl.blacklab.search.QueryExecutionContext, boolean)
     */
    @Override
    public abstract TermFrequencyList getCollocations(Annotation annotation, QueryExecutionContext ctx, boolean sort);

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#getCollocations()
     */
    @Override
    public abstract TermFrequencyList getCollocations();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#perDocResults()
     */
    @Override
    public abstract DocResults perDocResults();

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#groupedBy(nl.inl.blacklab.resultproperty.HitProperty)
     */
    @Override
    public abstract HitGroups groupedBy(final HitProperty criteria);

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#filteredBy(nl.inl.blacklab.resultproperty.HitProperty, nl.inl.blacklab.resultproperty.HitPropValue)
     */
    @Override
    public abstract Hits filteredBy(HitProperty property, HitPropValue value);

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#sortedBy(nl.inl.blacklab.resultproperty.HitProperty, boolean)
     */
    @Override
    public abstract Hits sortedBy(HitProperty sortProp, boolean reverseSort);

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#sortedBy(nl.inl.blacklab.resultproperty.HitProperty)
     */
    @Override
    public abstract Hits sortedBy(final HitProperty sortProp);

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#copyMaxHitsRetrieved(nl.inl.blacklab.search.results.Hits)
     */
    @Override
    public abstract void copyMaxHitsRetrieved(Hits copyFrom);

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#copy(nl.inl.blacklab.search.results.HitsSettings)
     */
    @Override
    public abstract HitsAbstract copy(HitsSettings settings);

    /** Id the next Hits instance will get */
    private static int nextHitsObjId = 0;

    private static synchronized int getNextHitsObjId() {
        return nextHitsObjId++;
    }

    /** Unique id of this Hits instance */
    protected final int hitsObjId = getNextHitsObjId();
    protected BlackLabIndex index;

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#getHitsObjId()
     */
    @Override
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

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#setPriorityLevel(nl.inl.util.ThreadPriority.Level)
     */
    @Override
    public void setPriorityLevel(ThreadPriority.Level level) {
        threadPriority.setPriorityLevel(level);
    }

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#getPriorityLevel()
     */
    @Override
    public ThreadPriority.Level getPriorityLevel() {
        return threadPriority.getPriorityLevel();
    }

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#index()
     */
    @Override
    public BlackLabIndex index() {
        return index;
    }

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#field()
     */
    @Override
    public AnnotatedField field() {
        return field;
    }

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#settings()
     */
    @Override
    public HitsSettings settings() {
        return settings;
    }

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#getThreadPriority()
     */
    @Override
    public ThreadPriority getThreadPriority() {
        return threadPriority;
    }

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#concordances(int)
     */
    @Override
    public Concordances concordances(int contextSize) {
        return new Concordances(this, contextSize);
    }

    /* (non-Javadoc)
     * @see nl.inl.blacklab.search.results.Hits#kwics(int)
     */
    @Override
    public Kwics kwics(int contextSize) {
        return new Kwics(this, contextSize);
    }

}
