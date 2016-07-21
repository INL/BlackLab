package nl.inl.blacklab.server.search;

import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a docs search and sort operation.
 */
public class JobDocsSorted extends JobWithDocs {

	private DocResults sourceResults;

	public JobDocsSorted(SearchManager searchMan, User user, SearchParameters par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// First, execute blocking docs search.
		SearchParameters parNoSort = par.copyWithout("sort");
		JobWithDocs search = searchMan.searchDocs(user, parNoSort);
		try {
			waitForJobToFinish(search);

			// Now, sort the docs.
			sourceResults = search.getDocResults();
			setPriorityInternal();
		} finally {
			search.decrRef();
			search = null;
		}
		String sortBy = par.getString("sort");
		if (sortBy == null)
			sortBy = "";
		boolean reverse = false;
		if (sortBy.length() > 0 && sortBy.charAt(0) == '-') {
			reverse = true;
			sortBy = sortBy.substring(1);
		}
		DocProperty sortProp = DocProperty.deserialize(sortBy);
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
			sourceResults.sort(sortProp, reverse); // TODO: add .sortedBy() same as in Hits
		}
		docResults = sourceResults; // client can use results
	}

	@Override
	protected void setPriorityInternal() {
		if (sourceResults != null)
			setDocsPriority(sourceResults);
	}

	@Override
	public Level getPriorityOfResultsObject() {
		return sourceResults == null ? Level.RUNNING : sourceResults.getPriorityLevel();
	}

	@Override
	public DataObjectMapElement toDataObject(boolean debugInfo) {
		DataObjectMapElement d = super.toDataObject(debugInfo);
		d.put("numberOfDocResults", docResults == null ? -1 : docResults.size());
		return d;
	}

}
