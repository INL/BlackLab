package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.SearchManager;

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
			return super.uniqueIdentifier() + sortSettings + ")";
		}

		@Override
		public DataObjectMapElement toDataObject() {
			DataObjectMapElement o = super.toDataObject();
			o.put("sortSettings", sortSettings);
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
		if (sortProp != null) {
			hits = hitsUnsorted.sortedBy(sortProp, sortSett.reverse());
		} else {
			// Be lenient of clients passing wrong sortBy values; simply ignore bad sort requests.
			hits = hitsUnsorted;
		}
		setPriorityInternal();
	}

	@Override
	public DataObjectMapElement toDataObject(boolean debugInfo) throws BlsException {
		DataObjectMapElement d = super.toDataObject(debugInfo);
		d.put("numberOfHits", hits == null ? -1 : hits.size());
		return d;
	}

}
