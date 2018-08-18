package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.resultproperty.HitProperty;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BadRequest;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents a hits search and sort operation.
 */
public class JobHitsGrouped extends JobWithHits {

    public static class JobDescHitsGrouped extends JobDescription {

        HitGroupSettings groupSettings;

        private HitGroupSortSettings groupSortSettings;

        public JobDescHitsGrouped(SearchParameters param, JobDescription hitsToGroup, SearchSettings searchSettings,
                HitGroupSettings groupSettings, HitGroupSortSettings groupSortSettings) {
            super(param, JobHitsGrouped.class, hitsToGroup, searchSettings);
            this.groupSettings = groupSettings;
            this.groupSortSettings = groupSortSettings;
        }

        @Override
        public HitGroupSettings getHitGroupSettings() {
            return groupSettings;
        }

        @Override
        public HitGroupSortSettings getHitGroupSortSettings() {
            return groupSortSettings;
        }

        @Override
        public String uniqueIdentifier() {
            return super.uniqueIdentifier() + groupSettings + ", " + groupSortSettings + ")";
        }

        @Override
        public void dataStreamEntries(DataStream ds) {
            super.dataStreamEntries(ds);
            ds.entry("groupSettings", groupSettings)
                    .entry("groupSortSettings", groupSortSettings);
        }

        @Override
        public String getUrlPath() {
            return "hits";
        }

    }

    private HitGroups groups;

    public JobHitsGrouped(SearchManager searchMan, User user, JobDescription par) throws BlsException {
        super(searchMan, user, par);
    }

    @Override
    protected void performSearch() throws BlsException {
        // Now, group the hits.
        hits = ((JobWithHits) inputJob).getHits();
        setPausedInternal();
        HitGroupSettings groupSett = jobDesc.getHitGroupSettings();
        if (groupSett == null)
            throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "No group property specified.");
        HitProperty groupProp = null;
        groupProp = HitProperty.deserialize(hits, groupSett.groupBy());
        if (groupProp == null)
            throw new BadRequest("UNKNOWN_GROUP_PROPERTY", "Unknown group property '" + groupSett.groupBy() + "'.");
        HitGroups theGroups = hits.group(groupProp, -1);

        HitGroupSortSettings sortSett = jobDesc.getHitGroupSortSettings();
        if (sortSett != null)
            theGroups = theGroups.sort(sortSett.sortBy());

        groups = theGroups; // we're done, caller can use the groups now
    }

    public HitGroups getGroups() {
        return groups;
    }

    @Override
    protected void dataStreamSubclassEntries(DataStream ds) {
        ds.entry("hitsRetrieved", hits == null ? -1 : hits.hitsProcessedSoFar())
                .entry("numberOfGroups", groups == null ? -1 : groups.size());
    }

    @Override
    protected void cleanup() {
        groups = null;
        super.cleanup();
    }
}
