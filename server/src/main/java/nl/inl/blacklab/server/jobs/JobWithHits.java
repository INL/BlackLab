package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.results.HitsAbstract;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * A search job that produces a Hits object
 */
public abstract class JobWithHits extends Job {

    /** The hits found */
    protected HitsAbstract hits;

    public JobWithHits(SearchManager searchMan, User user, JobDescription par) throws BlsException {
        super(searchMan, user, par);
    }

    public HitsAbstract getHits() {
        return hits;
    }

    @Override
    protected void dataStreamSubclassEntries(DataStream ds) {
        ds.entry("countHitsRetrieved", hits == null ? -1 : hits.docsProcessedSoFar());
        ds.entry("hasHitsObject", hits != null);
        if (hits != null) {
            ds.entry("hitsObjId", hits.getHitsObjId())
                    .entry("retrievedSoFar", hits.hitsProcessedSoFar())
                    .entry("doneFetchingHits", hits.doneProcessingAndCounting());
        }
    }

    @Override
    protected void cleanup() {
        hits = null;
        super.cleanup();
    }

    @Override
    protected HitsAbstract getObjectToPrioritize() {
        return hits;
    }

}
