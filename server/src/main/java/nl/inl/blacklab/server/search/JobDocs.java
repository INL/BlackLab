package nl.inl.blacklab.server.search;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.Forbidden;

/**
 * Represents a doc search operation.
 */
public class JobDocs extends JobWithDocs {

	public JobDocs(SearchManager searchMan, User user, Description par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// First, execute blocking hits search.
		if (jobDesc.getPattern() != null) {
			Description parNoSort = DescriptionImpl.jobHits(JobHits.class, jobDesc.getIndexName(), jobDesc.getPattern(),
					jobDesc.getFilterQuery(), null, jobDesc.getMaxSettings(), jobDesc.getSampleSettings(), jobDesc.getWindowSettings(),
					jobDesc.getContextSettings());
			JobWithHits hitsSearch = (JobWithHits) searchMan.search(user, parNoSort.hits());
			Hits hits;
			try {
				waitForJobToFinish(hitsSearch);
				// Now, get per document results
				hits = hitsSearch.getHits();
			} finally {
				hitsSearch.decrRef();
				hitsSearch = null;
			}
			ContextSettings contextSett = jobDesc.getContextSettings();
			hits.settings().setConcordanceType(contextSett.concType());
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
