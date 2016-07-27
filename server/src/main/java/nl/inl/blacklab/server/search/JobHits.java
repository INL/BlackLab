package nl.inl.blacklab.server.search;


import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;

import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;

/**
 * Represents a hit search operation.
 */
public class JobHits extends JobWithHits {

	/** The parsed pattern */
	protected TextPattern textPattern;

	/** The parsed filter */
	protected Filter filter;

	public JobHits(SearchManager searchMan, User user, Description par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		try {
			textPattern = jobDesc.getPattern();
			//debug(logger, "Textpattern: " + textPattern);
			Query q = jobDesc.getFilterQuery();
			filter = q == null ? null : new QueryWrapperFilter(q);
			try {
				hits = searcher.find(textPattern, filter);

				// Set the max retrieve/count value
				MaxSettings maxSettings = jobDesc.getMaxSettings();
				hits.settings().setMaxHitsToRetrieve(maxSettings.maxRetrieve());
				hits.settings().setMaxHitsToCount(maxSettings.maxCount());

			} catch (RuntimeException e) {
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
		return filter;
	}

	@Override
	protected void cleanup() {
		textPattern = null;
		filter = null;
		super.cleanup();
	}

	public static Description description(SearchManager searchMan, String indexName, TextPattern pattern, Query filterQuery,
			MaxSettings maxSettings, SampleSettings sampleSettings) {
		return DescriptionImpl.jobHits(JobHits.class, indexName, pattern, filterQuery, null, maxSettings,
				sampleSettings, null, null);
	}

}
