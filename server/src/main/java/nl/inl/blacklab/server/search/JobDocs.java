package nl.inl.blacklab.server.search;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.Forbidden;

/**
 * Represents a doc search operation.
 */
public class JobDocs extends JobWithDocs {

	public static class JobDescDocs extends JobDescription {

		Query filterQuery;

		public JobDescDocs(JobDescription hitsToGroup, Query filterQuery) {
			super(hitsToGroup);
			this.filterQuery = filterQuery;
		}

		@Override
		public Query getFilterQuery() {
			return filterQuery;
		}

		@Override
		public String uniqueIdentifier() {
			return "JDDescDocs [" + inputDesc + ", " + filterQuery + "]";
		}

		@Override
		public Job createJob(SearchManager searchMan, User user) throws BlsException {
			return new JobDocs(searchMan, user, this);
		}

		@Override
		public DataObject toDataObject() {
			DataObjectMapElement o = new DataObjectMapElement();
			o.put("jobClass", "JobHits");
			o.put("inputDesc", inputDesc.toString());
			o.put("filterQuery", filterQuery.toString());
			return o;
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
			//ContextSettings contextSett = jobDesc.getContextSettings();
			//hits.settings().setConcordanceType(contextSett.concType());
			//hits.settings().setContextSize(contextSett.size());
			docResults = hits.perDocResults();
		} else {
			// Documents only
			Query filterQuery = jobDesc.getFilterQuery();
			if (filterQuery == null) {
				if (SearchManager.isAllDocsQueryAllowed())
					filterQuery = new MatchAllDocsQuery();
				else
					throw new Forbidden("You must specify at least a filter query.");
			}
			docResults = searcher.queryDocuments(filterQuery);
		}
	}

}
