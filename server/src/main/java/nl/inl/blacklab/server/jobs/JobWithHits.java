package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * A search job that produces a Hits object
 */
public abstract class JobWithHits extends Job {

	/** The hits found */
	protected Hits hits;

	public JobWithHits(SearchManager searchMan, User user, JobDescription par) throws BlsException {
		super(searchMan, user, par);
	}

	public Hits getHits() {
		return hits;
	}

	@Override
	protected void dataStreamSubclassEntries(DataStream ds) {
        ds  .entry("countHitsRetrieved", hits == null ? -1 : hits.countSoFarDocsRetrieved());
        ds  .entry("hasHitsObject", hits != null);
        if (hits != null) {
            ds  .entry("hitsObjId", hits.getHitsObjId())
                .entry("retrievedSoFar", hits.countSoFarHitsRetrieved())
                .entry("doneFetchingHits", hits.doneFetchingHits());
        }
	}

	@Override
	protected void cleanup() {
		hits = null;
		super.cleanup();
	}

	@Override
	protected Hits getObjectToPrioritize() {
		return hits;
	}

}
