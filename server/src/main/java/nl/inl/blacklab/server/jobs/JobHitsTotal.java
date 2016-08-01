package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ServiceUnavailable;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents finding the total number of hits.
 */
public class JobHitsTotal extends Job {

	public static class JobDescHitsTotal extends JobDescription {

		public JobDescHitsTotal(JobDescription inputDesc) {
			super(JobHitsTotal.class, inputDesc);
		}

		@Override
		public String uniqueIdentifier() {
			return super.uniqueIdentifier() + "[]";
		}

		@Override
		public DataObjectMapElement toDataObject() {
			return super.toDataObject();
		}

	}

	private Hits hits = null;

	public JobHitsTotal(SearchManager searchMan, User user, JobDescription par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// Get the total number of hits (we ignore the value because you can monitor progress
		// and get the final total through the getHits() method yourself.
		hits = ((JobWithHits)inputJob).getHits();
		setPriorityInternal(); // make sure hits has the right priority
		hits.size();
		if (Thread.interrupted()) {
			throw new ServiceUnavailable("Determining total number of hits took too long, cancelled");
		}
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

	@Override
	protected Hits getObjectToPrioritize() {
		return hits;
	}

}
