package nl.inl.blacklab.server.search;

import nl.inl.blacklab.perdocument.DocGroups;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a hits search and sort operation.
 */
public class JobDocsGrouped extends Job {

	private DocGroups groups;

	private DocResults docResults;

	public JobDocsGrouped(SearchManager searchMan, User user, Description par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// First, execute blocking docs search.
		Description parNoGroup = DescriptionImpl.jobDocs(JobDocs.class, jobDesc.getIndexName(), jobDesc.getPattern(),
				jobDesc.getFilterQuery(), null, jobDesc.getMaxSettings(), jobDesc.getWindowSettings(), jobDesc.getContextSettings());
		JobWithDocs docsSearch = searchMan.searchDocs(user, parNoGroup);
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
