package nl.inl.blacklab.server.search;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.grouping.HitGroups;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a hits search and sort operation.
 */
public class JobHitsGrouped extends Job {

	public static class JobDescHitsGrouped extends JobDescriptionBasic {

		JobDescription inputDesc;

		HitGroupSettings groupSettings;

		public JobDescHitsGrouped(String indexName, JobDescription hitsToGroup, HitGroupSettings groupSettings) {
			super(indexName);
			this.inputDesc = hitsToGroup;
			this.groupSettings = groupSettings;
		}

		public JobDescription getInputDesc() {
			return inputDesc;
		}

		@Override
		public HitGroupSettings hitGroupSettings() {
			return groupSettings;
		}

		@Override
		public String uniqueIdentifier() {
			return "JDHitsGrouped [" + indexName + ", " + inputDesc + ", " + groupSettings + "]";
		}

		@Override
		public Job createJob(SearchManager searchMan, User user) throws BlsException {
			return new JobHitsGrouped(searchMan, user, this);
		}

		@Override
		public DataObject toDataObject() {
			DataObjectMapElement o = new DataObjectMapElement();
			o.put("jobClass", "JobHitsGrouped");
			o.put("indexName", indexName);
			o.put("inputDesc", inputDesc.toDataObject());
			o.put("groupSettings", groupSettings.toString());
			return o;
		}

	}

	private HitGroups groups;

	private Hits hits;

	public JobHitsGrouped(SearchManager searchMan, User user, JobDescHitsGrouped par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		JobDescHitsGrouped groupDesc = (JobDescHitsGrouped)jobDesc;

		// First, execute blocking hits search.
		JobWithHits hitsSearch = (JobWithHits) searchMan.search(user, groupDesc.getInputDesc());
		try {
			waitForJobToFinish(hitsSearch);

			// Now, group the hits.
			hits = hitsSearch.getHits();
			setPriorityInternal();
		} finally {
			hitsSearch.decrRef();
			hitsSearch = null;
		}
		HitGroupSettings groupSett = jobDesc.hitGroupSettings();
		HitProperty groupProp = null;
		groupProp = HitProperty.deserialize(hits, groupSett.groupBy());
		if (groupProp == null)
			throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupSett.groupBy() + "'.");
		HitGroups theGroups = hits.groupedBy(groupProp);

		HitGroupSortSettings sortSett = jobDesc.hitGroupSortSettings();
		if (sortSett != null)
			theGroups.sortGroups(sortSett.sortBy(), sortSett.reverse());

		groups = theGroups; // we're done, caller can use the groups now
	}

	public HitGroups getGroups() {
		return groups;
	}

	public Hits getHits() {
		return hits;
	}

	@Override
	protected void setPriorityInternal() {
		setHitsPriority(hits);
	}

	@Override
	public Level getPriorityOfResultsObject() {
		return hits == null ? Level.RUNNING : hits.getPriorityLevel();
	}

	@Override
	public DataObjectMapElement toDataObject(boolean debugInfo) throws BlsException {
		DataObjectMapElement d = super.toDataObject(debugInfo);
		d.put("hitsRetrieved", hits == null ? -1 : hits.countSoFarHitsRetrieved());
		d.put("numberOfGroups", groups == null ? -1 : groups.numberOfGroups());
		return d;
	}

	@Override
	protected void cleanup() {
		groups = null;
		hits = null;
		super.cleanup();
	}

}
