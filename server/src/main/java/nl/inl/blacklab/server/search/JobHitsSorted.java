package nl.inl.blacklab.server.search;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a hits search and sort operation.
 */
public class JobHitsSorted extends JobWithHits {

	public static class JobDescHitsSorted extends JobDescriptionBasic {

		JobDescription inputDesc;

		HitsSortSettings sortSettings;

		public JobDescHitsSorted(String indexName, JobDescription hitsToSort, HitsSortSettings sortSettings) {
			super(indexName);
			this.inputDesc = hitsToSort;
			this.sortSettings = sortSettings;
		}

		public JobDescription getInputDesc() {
			return inputDesc;
		}

		@Override
		public HitsSortSettings hitsSortSettings() {
			return sortSettings;
		}

		@Override
		public String uniqueIdentifier() {
			return "JDHitsSorted [" + indexName + ", " + inputDesc + ", " + sortSettings + "]";
		}

		@Override
		public Job createJob(SearchManager searchMan, User user) throws BlsException {
			return new JobHitsSorted(searchMan, user, this);
		}

		@Override
		public DataObject toDataObject() {
			DataObjectMapElement o = new DataObjectMapElement();
			o.put("jobClass", "JobHitsSorted");
			o.put("indexName", indexName);
			o.put("inputDesc", inputDesc.toDataObject());
			o.put("sortSettings", sortSettings.toString());
			return o;
		}

	}

	public JobHitsSorted(SearchManager searchMan, User user, JobDescription par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		JobDescHitsSorted sortDesc = (JobDescHitsSorted)jobDesc;

		// First, execute blocking hits search.
		JobWithHits hitsSearch = (JobWithHits) searchMan.search(user, sortDesc.getInputDesc());
		Hits hitsUnsorted;
		try {
			waitForJobToFinish(hitsSearch);

			// Now, sort the hits.
			hitsUnsorted = hitsSearch.getHits();
		} finally {
			hitsSearch.decrRef();
			hitsSearch = null;
		}
		HitsSortSettings sortSett = jobDesc.hitsSortSettings();
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
