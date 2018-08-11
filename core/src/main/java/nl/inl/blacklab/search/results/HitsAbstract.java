package nl.inl.blacklab.search.results;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.exceptions.ResultNotFound;
import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.TermFrequencyList;
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
    
    /** Information about the original query: index, field, max settings, max stats. */
    QueryInfo queryInfo;
    
    @Override
    public QueryInfo queryInfo() {
        return queryInfo;
    }
    
    /**
     * Helper object for pausing threads (making sure queries
     * don't hog the CPU for way too long).
     */
    protected ThreadPauser threadPauser;

    public HitsAbstract(QueryInfo queryInfo) {
        this.queryInfo = queryInfo;
        threadPauser = new ThreadPauser();
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
        return new HitsImpl(queryInfo, filtered);
    }

    @Override
    public HitGroups groupedBy(final HitProperty criteria) {
        return ResultsGrouper.fromHits(this, criteria);
    }

    @Override
    public DocResults perDocResults() {
        return DocResults.fromHits(queryInfo(), this);
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
        return new HitsWindow(this, first, windowSize);
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
