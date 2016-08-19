package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.perdocument.DocGroups;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents a hits search and sort operation.
 */
public class JobDocsGrouped extends Job {

	public static class JobDescDocsGrouped extends JobDescription {

		DocGroupSettings groupSettings;

		private DocGroupSortSettings groupSortSettings;

		public JobDescDocsGrouped(JobDescription docsToGroup, DocGroupSettings groupSettings, DocGroupSortSettings groupSortSettings) {
			super(JobDocsGrouped.class, docsToGroup);
			this.groupSettings = groupSettings;
			this.groupSortSettings = groupSortSettings;
		}

		@Override
		public DocGroupSettings getDocGroupSettings() {
			return groupSettings;
		}

		@Override
		public DocGroupSortSettings getDocGroupSortSettings() {
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

	private DocGroups groups;

	private DocResults docResults;

	public JobDocsGrouped(SearchManager searchMan, User user, JobDescription par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		docResults = ((JobWithDocs)inputJob).getDocResults();
		setPriorityInternal();
		DocGroupSettings groupSett = jobDesc.getDocGroupSettings();
		DocGroups theGroups = docResults.groupedBy(groupSett.groupBy());

		DocGroupSortSettings sortSett = jobDesc.getDocGroupSortSettings();
		if (sortSett != null)
			theGroups.sort(sortSett.sortBy(), sortSett.reverse());

		groups = theGroups; // we're done, caller can use the groups now
	}

	public DocGroups getGroups() {
		return groups;
	}

	public DocResults getDocResults() {
		return docResults;
	}

	@Override
	protected void dataStreamSubclassEntries(DataStream ds) {
		ds	.entry("numberOfDocResults", docResults == null ? -1 : docResults.size())
			.entry("numberOfGroups", groups == null ? -1 : groups.numberOfGroups());
	}

	@Override
	protected void cleanup() {
		groups = null;
		docResults = null;
		super.cleanup();
	}

	@Override
	protected DocResults getObjectToPrioritize() {
		return docResults;
	}

}
