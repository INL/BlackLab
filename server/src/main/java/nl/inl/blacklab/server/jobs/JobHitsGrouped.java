package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.grouping.HitGroups;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents a hits search and sort operation.
 */
public class JobHitsGrouped extends Job {

	public static class JobDescHitsGrouped extends JobDescription {

		HitGroupSettings groupSettings;

		private HitGroupSortSettings groupSortSettings;

		public JobDescHitsGrouped(JobDescription hitsToGroup, HitGroupSettings groupSettings, HitGroupSortSettings groupSortSettings) {
			super(JobHitsGrouped.class, hitsToGroup);
			this.groupSettings = groupSettings;
			this.groupSortSettings = groupSortSettings;
		}

		@Override
		public HitGroupSettings getHitGroupSettings() {
			return groupSettings;
		}

		@Override
		public HitGroupSortSettings getHitGroupSortSettings() {
			return groupSortSettings;
		}

		@Override
		public String uniqueIdentifier() {
			return super.uniqueIdentifier() + groupSettings + ", " + groupSortSettings + ")";
		}

		@Override
		public void dataStreamEntries(DataStream ds) {
			super.dataStreamEntries(ds);
			ds	.entry("groupSettings", groupSettings)
				.entry("groupSortSettings", groupSortSettings);
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
		if (groupSett == null)
			throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "No group property specified.");
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
	protected void dataStreamSubclassEntries(DataStream ds) {
		ds	.entry("hitsRetrieved", hits == null ? -1 : hits.countSoFarHitsRetrieved())
			.entry("numberOfGroups", groups == null ? -1 : groups.numberOfGroups());
	}

	@Override
	protected void cleanup() {
		groups = null;
		hits = null;
		super.cleanup();
	}

	@Override
	protected Hits getObjectToPrioritize() {
		return hits;
	}

}
