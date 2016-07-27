package nl.inl.blacklab.server.search;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ServiceUnavailable;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents finding the total number of hits.
 */
public class JobHitsTotal extends Job {

	private Hits hits = null;

	public JobHitsTotal(SearchManager searchMan, User user, Description par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// First, execute blocking hits search.
		JobWithHits hitsSearch = searchMan.searchHits(user, jobDesc);
		try {
			waitForJobToFinish(hitsSearch);

			// Get the total number of hits (we ignore the value because you can monitor progress
			// and get the final total through the getHits() method yourself.
			hits = hitsSearch.getHits();
			setPriorityInternal(); // make sure hits has the right priority
		} finally {
			hitsSearch.decrRef();
			hitsSearch = null;
		}
		hits.size();
		if (Thread.interrupted()) {
			throw new ServiceUnavailable("Determining total number of hits took too long, cancelled");
		}
	}

	@Override
	protected void setPriorityInternal() {
		setHitsPriority(hits);
	}

	@Override
	public Level getPriorityOfResultsObject() {
		return hits == null ? Level.RUNNING : hits.getPriorityLevel();
	}

	/**
	 * Returns the Hits object when available.
	 *
	 * @return the Hits object, or null if not available yet.
	 */
	public Hits getHits() {
		return hits;
	}

	@Override
	public DataObjectMapElement toDataObject(boolean debugInfo) throws BlsException {
		DataObjectMapElement d = super.toDataObject(debugInfo);
		d.put("hitsCounted", hits != null ? hits.countSoFarHitsCounted() : -1);
		return d;
	}

	@Override
	protected void cleanup() {
		hits = null;
		super.cleanup();
	}

	public static Description description(SearchManager searchMan, String indexName, TextPattern pattern, Query filterQuery,
			MaxSettings maxSettings, SampleSettings sampleSettings) {
		return DescriptionImpl.jobHits(JobHitsTotal.class, searchMan, indexName, pattern, filterQuery, null, maxSettings, sampleSettings, null, null);
	}

}
