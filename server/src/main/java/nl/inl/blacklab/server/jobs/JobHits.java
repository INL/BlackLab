package nl.inl.blacklab.server.jobs;

import org.apache.lucene.search.BooleanQuery.TooManyClauses;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.RegexpTooLargeException;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.blacklab.search.results.HitsSettings;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.exceptions.InternalServerError;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents a hit search operation.
 */
public class JobHits extends JobWithHits {

    public static class JobDescHits extends JobDescription {

        private String indexName;

        private TextPattern pattern;

        private Query filterQuery;

        private MaxSettings maxSettings;

        private ContextSettings contextSettings;

        public JobDescHits(SearchParameters param, SearchSettings searchSettings, String indexName, TextPattern pattern,
                Query filterQuery, MaxSettings maxSettings, ContextSettings contextSettings) {
            super(param, JobHits.class, null, searchSettings);
            this.indexName = indexName;
            this.pattern = pattern;
            this.filterQuery = filterQuery;
            this.maxSettings = maxSettings;
            this.contextSettings = contextSettings;
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
        public ContextSettings getContextSettings() {
            return contextSettings;
        }

        @Override
        public String uniqueIdentifier() {
            return super.uniqueIdentifier() + "index=" + getIndexName() + ", patt=" + pattern + ", filter="
                    + filterQuery + ", " +
                    maxSettings + ", " + contextSettings + ")";
        }

        @Override
        public void dataStreamEntries(DataStream ds) {
            super.dataStreamEntries(ds);
            ds.entry("pattern", pattern)
                    .entry("filterQuery", filterQuery)
                    .entry("maxSettings", maxSettings)
                    .entry("contextSettings", contextSettings);
        }

        @Override
        public String getUrlPath() {
            return "hits";
        }

    }

    /** The parsed pattern */
    protected TextPattern textPattern;

    /** The parsed filter */
    protected Query filter;

    public JobHits(SearchManager searchMan, User user, JobDescription par) throws BlsException {
        super(searchMan, user, par);
    }

    @Override
    protected void performSearch() throws BlsException {
        textPattern = jobDesc.getPattern();
        if (textPattern == null)
            throw new BadRequest("NO_PATTERN_GIVEN", "Text search pattern required. Please specify 'patt' parameter.");
        //debug(logger, "Textpattern: " + textPattern);
        filter = jobDesc.getFilterQuery();
        try {

            // In debug mode, we can experiment with the forward index matching NFA threshold this way.
            // Lower numbers means more NFAs. E.g. 10 means if adjacent words differ in frequency by a factor
            // of 10, create an NFA.
            SearchSettings searchSett = jobDesc.getSearchSettings();
            if (searchSett.isDebugMode() && searchSett.getFiMatchNfaFactor() != -1)
                ClauseCombinerNfa.setNfaThreshold(searchSett.getFiMatchNfaFactor());

            hits = searcher.find(textPattern, filter);

            // Set the max retrieve/count value
            MaxSettings maxSettings = jobDesc.getMaxSettings();
            HitsSettings hitsSettings = hits.settings();
            hitsSettings.setMaxHitsToRetrieve(maxSettings.maxRetrieve());
            hitsSettings.setMaxHitsToCount(maxSettings.maxCount());
            ContextSettings contextSettings = jobDesc.getContextSettings();
            hitsSettings.setConcordanceType(contextSettings.concType());
            hitsSettings.setContextSize(contextSettings.size());
        } catch (RegexpTooLargeException e) {
            throw new BadRequest("REGEXP_TOO_LARGE", "Regular expression too large.");
        } catch (TooManyClauses e) {
            throw new BadRequest("QUERY_TOO_BROAD",
                    "Query too broad, too many matching terms. Please be more specific.");
        } catch (RuntimeException e) {
            throw new InternalServerError("Internal error", 15, e);
        }
    }

    public TextPattern getTextPattern() {
        return textPattern;
    }

    public Query getDocumentFilter() {
        return filter;
    }

    @Override
    protected void cleanup() {
        textPattern = null;
        filter = null;
        super.cleanup();
    }

}
