package nl.inl.blacklab.server.search;

import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.Forbidden;

import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

/**
 * Represents a doc search operation.
 */
public class JobDocs extends JobWithDocs {

	public JobDocs(SearchManager searchMan, User user, SearchParameters par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// First, execute blocking hits search.
		String patt = par.getString("patt");
		if (patt != null && patt.length() > 0) {
			SearchParameters parNoSort = par.copyWithout("sort");
			JobWithHits hitsSearch = searchMan.searchHits(user, parNoSort);
			Hits hits;
			try {
				waitForJobToFinish(hitsSearch);
				// Now, get per document results
				hits = hitsSearch.getHits();
			} finally {
				hitsSearch.decrRef();
				hitsSearch = null;
			}
			hits.settings().setConcordanceType(par.getString("usecontent").equals("orig") ? ConcordanceType.CONTENT_STORE : ConcordanceType.FORWARD_INDEX);
			docResults = hits.perDocResults();
		} else {
			// Documents only
			Query filterQuery = SearchManager.parseFilter(searcher, par.getString("filter"), par.getString("filterlang"));
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
