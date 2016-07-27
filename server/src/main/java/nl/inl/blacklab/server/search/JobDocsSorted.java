package nl.inl.blacklab.server.search;

import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a docs search and sort operation.
 */
public class JobDocsSorted extends JobWithDocs {

	private DocResults sourceResults;

	public JobDocsSorted(SearchManager searchMan, User user, Description par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// First, execute blocking docs search.
		Description parNoSort = DescriptionImpl.jobDocs(JobDocs.class, jobDesc.getIndexName(), jobDesc.getPattern(), jobDesc.getFilterQuery(),
				null, jobDesc.getMaxSettings(), jobDesc.getWindowSettings(), jobDesc.getContextSettings());
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
		DocSortSettings docSortSett = jobDesc.docSortSettings();
		if (docSortSett.sortBy() != null) {
			// Be lenient of clients passing wrong sortBy values,
			// e.g. trying to sort a per-document search by hit context.
			// The problem is that applications might remember your
			// preferred sort and pass it with subsequent searches, even
			// if that particular sort cannot be performed on that type of search.
			// We don't want the client to have to validate this, so we simply
			// ignore sort requests we can't carry out.
			sourceResults.sort(docSortSett.sortBy(), docSortSett.reverse()); // TODO: add .sortedBy() same as in Hits
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
	public DataObjectMapElement toDataObject(boolean debugInfo) throws BlsException {
		DataObjectMapElement d = super.toDataObject(debugInfo);
		d.put("numberOfDocResults", docResults == null ? -1 : docResults.size());
		return d;
	}

}
