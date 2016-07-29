package nl.inl.blacklab.server.search;

import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a docs search and sort operation.
 */
public class JobDocsSorted extends JobWithDocs {

	public static class JobDescDocsSorted extends JobDescriptionBasic {

		JobDescription inputDesc;

		DocSortSettings sortSettings;

		public JobDescDocsSorted(String indexName, JobDescription hitsToSort, DocSortSettings sortSettings) {
			super(indexName);
			this.inputDesc = hitsToSort;
			this.sortSettings = sortSettings;
		}

		public JobDescription getInputDesc() {
			return inputDesc;
		}

		@Override
		public DocSortSettings docSortSettings() {
			return sortSettings;
		}

		@Override
		public String uniqueIdentifier() {
			return "JDDocsSorted [" + indexName + ", " + inputDesc + ", " + sortSettings + "]";
		}

		@Override
		public Job createJob(SearchManager searchMan, User user) throws BlsException {
			return new JobDocsSorted(searchMan, user, this);
		}

		@Override
		public DataObject toDataObject() {
			DataObjectMapElement o = new DataObjectMapElement();
			o.put("jobClass", "JobDocsSorted");
			o.put("indexName", indexName);
			o.put("inputDesc", inputDesc.toDataObject());
			o.put("sortSettings", sortSettings.toString());
			return o;
		}

	}

	private DocResults sourceResults;

	public JobDocsSorted(SearchManager searchMan, User user, JobDescDocsSorted par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		JobDescDocsSorted docsSortedDesc = (JobDescDocsSorted)jobDesc;

		// First, execute blocking docs search.
		JobWithDocs search = (JobWithDocs) searchMan.search(user, docsSortedDesc.getInputDesc());
		try {
			waitForJobToFinish(search);

			sourceResults = search.getDocResults();
			setPriorityInternal();
		} finally {
			search.decrRef();
			search = null;
		}
		// Now, sort the docs.
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
