package nl.inl.blacklab.server.jobs;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.Forbidden;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents a doc search operation.
 */
public class JobDocs extends JobWithDocs {

	public static class JobDescDocs extends JobDescription {

		Query filterQuery;

		private String indexName;

		public JobDescDocs(SearchParameters param, JobDescription hitsToGroup, SearchSettings searchSettings, Query filterQuery, String indexName) {
			super(param, JobDocs.class, hitsToGroup, searchSettings);
			this.filterQuery = filterQuery;
			if (hitsToGroup == null) {
				this.indexName = indexName;
			} else {
				this.indexName = null;
				if (!indexName.equals(inputDesc.getIndexName()))
					throw new IllegalArgumentException("Mismatch between indexnames!");
			}
		}

		@Override
		public String getIndexName() {
			if (indexName != null)
				return indexName;
			return super.getIndexName();
		}

		@Override
		public Query getFilterQuery() {
			return filterQuery;
		}

		@Override
		public String uniqueIdentifier() {
			return super.uniqueIdentifier() + indexName + filterQuery + ")";
		}

		@Override
		public void dataStreamEntries(DataStream ds) {
			super.dataStreamEntries(ds);
			ds	.entry("filterQuery", filterQuery);
		}

		@Override
		public String getUrlPath() {
			return "docs";
		}

	}

	public JobDocs(SearchManager searchMan, User user, JobDescription par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		if (inputJob != null) {
			JobWithHits hitsSearch = (JobWithHits)inputJob;
			Hits hits = hitsSearch.getHits();
			// Now, get per document results
			docResults = hits.perDocResults();
		} else {
			// Documents only
			Query filterQuery = jobDesc.getFilterQuery();
			if (filterQuery == null) {
				if (searchMan.config().isAllDocsQueryAllowed())
					filterQuery = new MatchAllDocsQuery();
				else
					throw new Forbidden("You must specify at least a filter query.");
			}
			docResults = searcher.queryDocuments(filterQuery);
		}
	}

}
