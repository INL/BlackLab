package nl.inl.blacklab.server.search;

import nl.inl.blacklab.perdocument.DocGroups;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a hits search and sort operation.
 */
public class JobDocsGrouped extends Job {

	public static class JobDescDocsGrouped extends JobDescriptionBasic {

		JobDescription inputDesc;

		DocGroupSettings groupSettings;

		public JobDescDocsGrouped(String indexName, JobDescription docsToGroup, DocGroupSettings groupSettings) {
			super(indexName);
			this.inputDesc = docsToGroup;
			this.groupSettings = groupSettings;
		}

		public JobDescription getInputDesc() {
			return inputDesc;
		}

		@Override
		public DocGroupSettings docGroupSettings() {
			return groupSettings;
		}

		@Override
		public String uniqueIdentifier() {
			return "JDDocsGrouped [" + indexName + ", " + inputDesc + ", " + groupSettings + "]";
		}

		@Override
		public Job createJob(SearchManager searchMan, User user) throws BlsException {
			return new JobDocsGrouped(searchMan, user, this);
		}

		@Override
		public DataObject toDataObject() {
			DataObjectMapElement o = new DataObjectMapElement();
			o.put("jobClass", "JobDocsGrouped");
			o.put("indexName", indexName);
			o.put("inputDesc", inputDesc.toDataObject());
			o.put("groupSettings", groupSettings.toString());
			return o;
		}

	}

	private DocGroups groups;

	private DocResults docResults;

	public JobDocsGrouped(SearchManager searchMan, User user, JobDescDocsGrouped par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		JobDescDocsGrouped docGroupDesc = (JobDescDocsGrouped)jobDesc;

		// First, execute blocking docs search.
		JobWithDocs docsSearch = (JobWithDocs) searchMan.search(user, docGroupDesc.getInputDesc());
		try {
			waitForJobToFinish(docsSearch);

			// Now, group the docs.
			docResults = docsSearch.getDocResults();
			setPriorityInternal();
		} finally {
			docsSearch.decrRef();
			docsSearch = null;
		}
		DocGroupSettings groupSett = jobDesc.docGroupSettings();
		DocGroups theGroups = docResults.groupedBy(groupSett.groupBy());

		DocGroupSortSettings sortSett = jobDesc.docGroupSortSettings();
		if (sortSett != null)
			theGroups.sort(sortSett.sortBy(), sortSett.reverse());

		groups = theGroups; // we're done, caller can use the groups now
	}

	@Override
	protected void setPriorityInternal() {
		if (docResults != null)
			setDocsPriority(docResults);
	}

	@Override
	public Level getPriorityOfResultsObject() {
		return docResults == null ? Level.RUNNING : docResults.getPriorityLevel();
	}

	public DocGroups getGroups() {
		return groups;
	}

	public DocResults getDocResults() {
		return docResults;
	}

	@Override
	public DataObjectMapElement toDataObject(boolean debugInfo) throws BlsException {
		DataObjectMapElement d = super.toDataObject(debugInfo);
		d.put("numberOfDocResults", docResults == null ? -1 : docResults.size());
		d.put("numberOfGroups", groups == null ? -1 : groups.numberOfGroups());
		return d;
	}

	@Override
	protected void cleanup() {
		groups = null;
		docResults = null;
		super.cleanup();
	}



}
