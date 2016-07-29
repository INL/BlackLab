package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a hits search and sort operation.
 */
public class JobHitsSorted extends JobWithHits {

	public static class JobDescHitsSorted extends JobDescription {

		HitSortSettings sortSettings;

		public JobDescHitsSorted(JobDescription hitsToSort, HitSortSettings sortSettings) {
			super(JobHitsSorted.class, hitsToSort);
			this.sortSettings = sortSettings;
		}

		@Override
		public HitSortSettings getHitSortSettings() {
			return sortSettings;
		}

		@Override
		public String uniqueIdentifier() {
			return super.uniqueIdentifier() + "[" + sortSettings + "]";
		}

		@Override
		public DataObjectMapElement toDataObject() {
			DataObjectMapElement o = super.toDataObject();
			o.put("sortSettings", sortSettings.toString());
			return o;
		}

	}

	public JobHitsSorted(SearchManager searchMan, User user, JobDescription par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// Now, sort the hits.
		Hits hitsUnsorted = ((JobWithHits)inputJob).getHits();
		HitSortSettings sortSett = jobDesc.getHitSortSettings();
		HitProperty sortProp = HitProperty.deserialize(hitsUnsorted, sortSett.sortBy());
		/*if (sortProp == null)
			throw new QueryException("UNKNOWN_SORT_PROPERTY", "Unknown sort property '" + sortBy + "'.");
		*/
		if (sortProp != null) {
			// Be lenient of clients passing wrong sortBy values,
			// e.g. trying to sort a per-document search by hit context.
			// The problem is that applications might remember your
			// preferred sort and pass it with subsequent searches, even
			// if that particular sort cannot be performed on that type of search.
			// We don't want the client to have to validate this, so we simply
			// ignore sort requests we can't carry out.
			hits = hitsUnsorted.sortedBy(sortProp, sortSett.reverse());
		} else
			hits = hitsUnsorted;
		setPriorityInternal();
	}

	@Override
	protected void setPriorityInternal() {
		if (hits != null)
			setHitsPriority(hits);
	}

	@Override
	public Level getPriorityOfResultsObject() {
		return hits == null ? Level.RUNNING : hits.getPriorityLevel();
	}

	@Override
	public DataObjectMapElement toDataObject(boolean debugInfo) throws BlsException {
		DataObjectMapElement d = super.toDataObject(debugInfo);
		d.put("numberOfHits", hits == null ? -1 : hits.size());
		return d;
	}

}
