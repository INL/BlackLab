package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.grouping.HitGroups;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a hits search and sort operation.
 */
public class JobHitsGrouped extends Job {

	public static class JobDescHitsGrouped extends JobDescription {

		HitGroupSettings groupSettings;

		public JobDescHitsGrouped(JobDescription hitsToGroup, HitGroupSettings groupSettings) {
			super(JobHitsGrouped.class, hitsToGroup);
			this.groupSettings = groupSettings;
		}

		@Override
		public HitGroupSettings getHitGroupSettings() {
			return groupSettings;
		}

		@Override
		public String uniqueIdentifier() {
			return super.uniqueIdentifier() + "[" + groupSettings + "]";
		}

		@Override
		public DataObjectMapElement toDataObject() {
			DataObjectMapElement o = super.toDataObject();
			o.put("groupSettings", groupSettings.toString());
			return o;
		}

	}

	private HitGroups groups;

	private Hits hits;

	public JobHitsGrouped(SearchManager searchMan, User user, JobDescription par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// Now, group the hits.
		hits = ((JobWithHits)inputJob).getHits();
		setPriorityInternal();
		HitGroupSettings groupSett = jobDesc.getHitGroupSettings();
		HitProperty groupProp = null;
		groupProp = HitProperty.deserialize(hits, groupSett.groupBy());
		if (groupProp == null)
			throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupSett.groupBy() + "'.");
		HitGroups theGroups = hits.groupedBy(groupProp);

		HitGroupSortSettings sortSett = jobDesc.getHitGroupSortSettings();
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
