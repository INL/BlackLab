package nl.inl.blacklab.server.search;

import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.ServiceUnavailable;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents finding the total number of docs.
 */
public class JobDocsTotal extends Job {

	public static class JobDescDocsTotal extends JobDescriptionBasic {

		JobDescription inputDesc;

		public JobDescDocsTotal(String indexName, JobDescription inputDesc) {
			super(indexName);
			this.inputDesc = inputDesc;
		}

		public JobDescription getInputDesc() {
			return inputDesc;
		}

		@Override
		public String uniqueIdentifier() {
			return "JDDocsTotal [" + indexName + ", " + inputDesc + "]";
		}

		@Override
		public Job createJob(SearchManager searchMan, User user) throws BlsException {
			return new JobDocsTotal(searchMan, user, this);
		}

		@Override
		public DataObject toDataObject() {
			DataObjectMapElement o = new DataObjectMapElement();
			o.put("jobClass", "JobDocsTotal");
			o.put("indexName", indexName);
			o.put("inputDesc", inputDesc.toDataObject());
			return o;
		}

	}

	private DocResults docResults = null;

	public JobDocsTotal(SearchManager searchMan, User user, JobDescDocsTotal par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		JobDescDocsTotal docsTotalDesc = (JobDescDocsTotal)jobDesc;

		// First, execute blocking docs search.
		JobWithDocs docsSearch = (JobWithDocs) searchMan.search(user, docsTotalDesc.getInputDesc());
		try {
			waitForJobToFinish(docsSearch);

			// Get the total number of docs (we ignore the return value because you can monitor progress
			// and get the final total through the getDocResults() method yourself.
			docResults = docsSearch.getDocResults();
			setPriorityInternal(); // make sure docResults has the right priority
		} finally {
			docsSearch.decrRef();
			docsSearch = null;
		}
		docResults.size();
		if (Thread.interrupted()) {
			throw new ServiceUnavailable("Determining total number of docs took too long, cancelled");
		}
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

	/**
	 * Returns the DocResults object when available.
	 *
	 * @return the DocResults object, or null if not available yet.
	 */
	public DocResults getDocResults() {
		return docResults;
	}

	@Override
	public DataObjectMapElement toDataObject(boolean debugInfo) throws BlsException {
		DataObjectMapElement d = super.toDataObject(debugInfo);
		d.put("docsCounted", docResults.getOriginalHits() != null ? docResults.getOriginalHits().countSoFarDocsCounted() : -1);
		return d;
	}

	@Override
	protected void cleanup() {
		docResults = null;
		super.cleanup();
	}

}
