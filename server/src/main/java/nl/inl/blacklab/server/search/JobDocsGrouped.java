package nl.inl.blacklab.server.search;

import nl.inl.blacklab.perdocument.DocGroupProperty;
import nl.inl.blacklab.perdocument.DocGroups;
import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a hits search and sort operation.
 */
public class JobDocsGrouped extends Job {

	private DocGroups groups;

	private DocResults docResults;

	public JobDocsGrouped(SearchManager searchMan, User user, SearchParameters par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// First, execute blocking docs search.
		SearchParameters parNoGroup = par.copyWithout("group", "sort");
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
		String groupBy = par.getString("group");
		DocProperty groupProp = null;
		if (groupBy == null)
			groupBy = "";
		groupProp = DocProperty.deserialize(groupBy);
		if (groupProp == null)
			throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupBy + "'.");
		DocGroups theGroups = docResults.groupedBy(groupProp);

		String sortBy = par.getString("sort");
		if (sortBy == null)
			sortBy = "";
		boolean reverse = false;
		if (sortBy.length() > 0 && sortBy.charAt(0) == '-') {
			reverse = true;
			sortBy = sortBy.substring(1);
		}
		DocGroupProperty sortProp = DocGroupProperty.deserialize(sortBy);
		theGroups.sort(sortProp, reverse);

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
	public DataObjectMapElement toDataObject(boolean debugInfo) {
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
