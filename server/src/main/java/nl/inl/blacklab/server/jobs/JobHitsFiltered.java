package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.resultproperty.HitPropValue;
import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents a hits filter operation
 */
public class JobHitsFiltered extends JobWithHits {

    public static class JobDescHitsFiltered extends JobDescription {

        HitFilterSettings filterSettings;

        public JobDescHitsFiltered(SearchParameters param, JobDescription hitsToFilter, SearchSettings searchSettings,
                HitFilterSettings filterSettings) {
            super(param, JobHitsFiltered.class, hitsToFilter, searchSettings);
            this.filterSettings = filterSettings;
        }

        @Override
        public HitFilterSettings getHitFilterSettings() {
            return filterSettings;
        }

        @Override
        public String uniqueIdentifier() {
            return super.uniqueIdentifier() + filterSettings + ")";
        }

        @Override
        public void dataStreamEntries(DataStream ds) {
            super.dataStreamEntries(ds);
            ds.entry("filterSettings", filterSettings);
        }

        @Override
        public String getUrlPath() {
            return "hits";
        }

    }

    public JobHitsFiltered(SearchManager searchMan, User user, JobDescription par) throws BlsException {
        super(searchMan, user, par);
    }

    @Override
    protected void performSearch() throws BlsException {
        // Now, filter the hits.
        Hits hitsUnfiltered = ((JobWithHits) inputJob).getHits();
        HitFilterSettings filterSett = jobDesc.getHitFilterSettings();
        HitProperty prop = HitProperty.deserialize(hitsUnfiltered, filterSett.getProperty());
        HitPropValue value = HitPropValue.deserialize(hitsUnfiltered, filterSett.getValue());
        if (prop == null || value == null) {
            throw new BadRequest("ERROR_IN_HITFILTER", "Incorrect hit filter property of value specified.");
        }

        hits = hitsUnfiltered.filteredBy(prop, value);
        setPausedInternal();
    }

    @Override
    protected void dataStreamSubclassEntries(DataStream ds) {
        super.dataStreamSubclassEntries(ds);
        ds.entry("numberOfHits", hits == null ? -1 : hits.size());
    }
}
