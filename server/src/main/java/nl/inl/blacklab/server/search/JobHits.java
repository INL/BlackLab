package nl.inl.blacklab.server.search;


import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryWrapperFilter;

import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;

/**
 * Represents a hit search operation.
 */
public class JobHits extends JobWithHits {

	public static class JobDescHits extends JobDescription {

		private String indexName;

		private TextPattern pattern;

		private Query filterQuery;

		private MaxSettings maxSettings;

		public JobDescHits(String indexName, TextPattern pattern, Query filterQuery, MaxSettings maxSettings) {
			super(null);
			this.indexName = indexName;
			this.pattern = pattern;
			this.filterQuery = filterQuery;
			this.maxSettings = maxSettings;
		}

		@Override
		public String getIndexName() {
			return indexName;
		}

		@Override
		public TextPattern getPattern() {
			return pattern;
		}

		@Override
		public Query getFilterQuery() {
			return filterQuery;
		}

		@Override
		public MaxSettings getMaxSettings() {
			return maxSettings;
		}

		@Override
		public String uniqueIdentifier() {
			return "JDHits [" + getIndexName() + ", " + pattern + ", " + filterQuery + ", " + maxSettings + "]";
		}

		@Override
		public Job createJob(SearchManager searchMan, User user) throws BlsException {
			return new JobHits(searchMan, user, this);
		}

		@Override
		public DataObject toDataObject() {
			DataObjectMapElement o = new DataObjectMapElement();
			o.put("jobClass", "JobHits");
			o.put("pattern", pattern.toString());
			o.put("filterQuery", filterQuery.toString());
			o.put("maxSettings", maxSettings.toString());
			return o;
		}

	}

	/** The parsed pattern */
	protected TextPattern textPattern;

	/** The parsed filter */
	protected Filter filter;

	public JobHits(SearchManager searchMan, User user, JobDescription par) throws BlsException {
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

}
