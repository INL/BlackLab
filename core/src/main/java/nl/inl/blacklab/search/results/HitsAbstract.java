package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.ResultNotFound;
import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
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
     * The field these hits came from (will also be used as concordance field)
     */
    protected AnnotatedField field;
    
    /**
     * Whether or not we exceed the max. hits to process/count.
     */
    protected MaxStats maxStats;

    /**
     * Settings for retrieving hits.
     */
    protected HitsSettings settings;
    
    /**
     * Helper object for pausing threads (making sure queries
     * don't hog the CPU for way too long).
     */
    protected ThreadPauser threadPauser;

    public HitsAbstract(BlackLabIndex index, AnnotatedField field, HitsSettings settings, MaxStats maxStats) {
        this.index = index;
        this.field = field;
        this.settings = settings == null ? index.hitsSettings() : settings;
        this.maxStats = maxStats;
        threadPauser = new ThreadPauser();
    }

    public MaxStats maxStats() {
        return maxStats;
    }
    
    @Override
    public int size() {
        return hitsProcessedTotal();
    }

    @Override
    public Hits filteredBy(HitProperty property, HitPropValue value) {
        List<Annotation> requiredContext = property.needsContext();
        property.setContexts(new Contexts(this, requiredContext, property.needsContextSize()));

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
    public TermFrequencyList collocations(int contextSize) {
        return TermFrequencyList.collocations(contextSize, this, null, null, true);
    }

    @Override
    public synchronized TermFrequencyList collocations(int contextSize, Annotation annotation, QueryExecutionContext ctx, boolean sort) {
        return TermFrequencyList.collocations(contextSize, this, annotation, ctx, sort);
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
    public Concordances concordances(int contextSize, ConcordanceType type) {
        return new Concordances(this, type, contextSize);
    }

    @Override
    public Kwics kwics(int contextSize) {
        return new Kwics(this, contextSize);
    }

}
