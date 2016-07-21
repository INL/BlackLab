package nl.inl.blacklab.server.search;


import nl.inl.blacklab.search.HitsSample;
import nl.inl.blacklab.search.SingleDocIdFilter;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;

import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;

/**
 * Represents a hit search operation.
 */
public class JobHits extends JobWithHits {

	/** The parsed pattern */
	protected TextPattern textPattern;

	/** The parsed filter */
	protected Filter filterQuery;

	public JobHits(SearchManager searchMan, User user, SearchParameters par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		try {
			textPattern = searchMan.parsePatt(searcher, par.getString("patt"), par.getString("pattlang"));
			//debug(logger, "Textpattern: " + textPattern);
			Query q;
			String docId = par.getString("docpid");
			if (docId != null) {
				// Only hits in 1 doc (for highlighting)
				int luceneDocId = SearchManager.getLuceneDocIdFromPid(searcher, docId);
				if (luceneDocId < 0)
					throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docId + "' not found.");
				filterQuery = new SingleDocIdFilter(luceneDocId);
				debug(logger, "Filtering on single doc-id");
			} else {
				// Filter query
				q = SearchManager.parseFilter(searcher, par.getString("filter"), par.getString("filterlang"));
				filterQuery = q == null ? null : new QueryWrapperFilter(q);
			}
			try {
				hits = searcher.find(textPattern, filterQuery);

				// Set the max retrieve/count value
				int maxRetrieve = par.getInteger("maxretrieve");
				if (searchMan.getMaxHitsToRetrieveAllowed() >= 0 && maxRetrieve > searchMan.getMaxHitsToRetrieveAllowed()) {
					maxRetrieve = searchMan.getMaxHitsToRetrieveAllowed();
				}
				int maxCount = par.getInteger("maxcount");
				if (searchMan.getMaxHitsToCountAllowed() >= 0 && maxCount > searchMan.getMaxHitsToCountAllowed()) {
					maxCount = searchMan.getMaxHitsToCountAllowed();
				}
				hits.settings().setMaxHitsToRetrieve(maxRetrieve);
				hits.settings().setMaxHitsToCount(maxCount);

				// Do we want to take a smaller sample of all the hits?
				float samplePercentage = par.containsKey("sample") ? par.getFloat("sample") : -1f;
				int sampleNum = par.containsKey("samplenum") ? par.getInteger("samplenum") : -1;
				long sampleSeed = par.containsKey("sampleseed") ? par.getLong("sampleseed") : HitsSample.RANDOM_SEED;
				if (samplePercentage >= 0) {
					hits = HitsSample.fromHits(hits, samplePercentage / 100f, sampleSeed);
				} else if (sampleNum >= 0) {
					hits = HitsSample.fromHits(hits, sampleNum, sampleSeed);
				}

			} catch (RuntimeException e) {
				// TODO: catch a more specific exception!
				e.printStackTrace();
				throw new InternalServerError("Internal error", 15, e);
			}
		} catch (TooManyClauses e) {
			throw new BadRequest("QUERY_TOO_BROAD", "Query too broad, too many matching terms. Please be more specific.");
		}
	}

	public TextPattern getTextPattern() {
		return textPattern;
	}

	public Filter getDocumentFilter() {
		return filterQuery;
	}

	@Override
	protected void cleanup() {
		textPattern = null;
		filterQuery = null;
		super.cleanup();
	}

}
