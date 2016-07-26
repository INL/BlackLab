package nl.inl.blacklab.server.search;


import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;

import nl.inl.blacklab.search.HitsSample;
import nl.inl.blacklab.search.SingleDocIdFilter;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.exceptions.NotFound;

/**
 * Represents a hit search operation.
 */
public class JobHits extends JobWithHits {

	/** The parsed pattern */
	protected TextPattern textPattern;

	/** The parsed filter */
	protected Filter filterQuery;

	public JobHits(SearchManager searchMan, User user, Description par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		try {
			textPattern = jobDesc.getPattern();
			//debug(logger, "Textpattern: " + textPattern);
			Query q;
			String docId = jobDesc.getDocPid();
			if (docId != null) {
				// Only hits in 1 doc (for highlighting)
				int luceneDocId = SearchManager.getLuceneDocIdFromPid(searcher, docId);
				if (luceneDocId < 0)
					throw new NotFound("DOC_NOT_FOUND", "Document with pid '" + docId + "' not found.");
				filterQuery = new SingleDocIdFilter(luceneDocId);
				debug(logger, "Filtering on single doc-id");
			} else {
				// Filter query
				q = jobDesc.getFilterQuery();
				filterQuery = q == null ? null : new QueryWrapperFilter(q);
			}
			try {
				hits = searcher.find(textPattern, filterQuery);

				// Set the max retrieve/count value
				MaxSettings maxSettings = jobDesc.getMaxSettings();
				hits.settings().setMaxHitsToRetrieve(maxSettings.maxRetrieve());
				hits.settings().setMaxHitsToCount(maxSettings.maxCount());

				// Do we want to take a smaller sample of all the hits?
				SampleSettings sample = jobDesc.getSampleSettings();
				if (sample.percentage() >= 0) {
					hits = HitsSample.fromHits(hits, sample.percentage() / 100f, sample.seed());
				} else if (sample.number() >= 0) {
					hits = HitsSample.fromHits(hits, sample.number(), sample.seed());
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

	public static Description description(SearchManager searchMan, String indexName, TextPattern pattern, Query filterQuery,
			String docPid, MaxSettings maxSettings, SampleSettings sampleSettings) {
		return DescriptionImpl.jobHits(JobHits.class, searchMan, indexName, pattern, filterQuery, null, docPid, maxSettings, sampleSettings, null, null);
	}

}
