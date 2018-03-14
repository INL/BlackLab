package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents finding the total number of docs.
 */
public class JobDocsTotal extends JobWithDocs {

	public static class JobDescDocsTotal extends JobDescription {

		JobDescription inputDesc;

		public JobDescDocsTotal(SearchParameters param, JobDescription inputDesc, SearchSettings searchSettings) {
			super(param, JobDocsTotal.class, inputDesc, searchSettings);
		}

		@Override
		public String uniqueIdentifier() {
			return super.uniqueIdentifier() + ")";
		}

		@Override
		public String getUrlPath() {
			return "docs";
		}

	}

	public JobDocsTotal(SearchManager searchMan, User user, JobDescription par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	protected void performSearch() throws BlsException {
		// Get the total number of docs (we ignore the return value because you can monitor progress
		// and get the final total through the getDocResults() method yourself.
		docResults = ((JobWithDocs)inputJob).getDocResults();
		setPriorityInternal(); // make sure docResults has the right priority
		docResults.size();
		if (Thread.interrupted()) {
			// We don't throw anymore because that will cause this error to re-throw even when we just
			// want to look at a page of results. maxHitsCounted is set to true, however, so the application
			// can detect that we stopped counting at some point.
			//throw new ServiceUnavailable("Determining total number of docs took too long, cancelled");
			logger.warn("Determining total number of docs took too long, cancelled");
		}
	}

	@Override
	protected void dataStreamSubclassEntries(DataStream ds) {
		ds	.entry("docsCounted", docResults.getOriginalHits() != null ? docResults.getOriginalHits().countSoFarDocsCounted() : -1);
	}
}
