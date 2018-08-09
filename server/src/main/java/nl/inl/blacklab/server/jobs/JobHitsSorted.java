package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.results.HitsAbstract;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents a hits search and sort operation.
 */
public class JobHitsSorted extends JobWithHits {

    public static class JobDescHitsSorted extends JobDescription {

        HitSortSettings sortSettings;

        public JobDescHitsSorted(SearchParameters param, JobDescription hitsToSort, SearchSettings searchSettings,
                HitSortSettings sortSettings) {
            super(param, JobHitsSorted.class, hitsToSort, searchSettings);
            this.sortSettings = sortSettings;
        }

        @Override
        public HitSortSettings getHitSortSettings() {
            return sortSettings;
        }

        @Override
        public String uniqueIdentifier() {
            return super.uniqueIdentifier() + sortSettings + ")";
        }

        @Override
        public void dataStreamEntries(DataStream ds) {
            super.dataStreamEntries(ds);
            ds.entry("sortSettings", sortSettings);
        }

        @Override
        public String getUrlPath() {
            return "hits";
        }

    }

    public JobHitsSorted(SearchManager searchMan, User user, JobDescription par) throws BlsException {
        super(searchMan, user, par);
    }

    @Override
    protected void performSearch() throws BlsException {
        // Now, sort the hits.
        HitsAbstract hitsUnsorted = ((JobWithHits) inputJob).getHits();
        HitSortSettings sortSett = jobDesc.getHitSortSettings();
        HitProperty sortProp = HitProperty.deserialize(hitsUnsorted, sortSett.sortBy());
        if (sortProp != null) {
            hits = hitsUnsorted.sortedBy(sortProp, sortSett.reverse());
        } else {
            // Be lenient of clients passing wrong sortBy values; simply ignore bad sort requests.
            hits = hitsUnsorted;
        }
        setPriorityInternal();
    }

    @Override
    protected void dataStreamSubclassEntries(DataStream ds) {
        super.dataStreamSubclassEntries(ds);
        ds.entry("numberOfHits", hits == null ? -1 : hits.size());
    }

}
