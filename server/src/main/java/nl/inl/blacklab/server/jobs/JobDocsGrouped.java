package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents a hits search and sort operation.
 */
public class JobDocsGrouped extends JobWithDocs {

    public static class JobDescDocsGrouped extends JobDescription {

        DocGroupSettings groupSettings;

        private DocGroupSortSettings groupSortSettings;

        public JobDescDocsGrouped(SearchParameters param, JobDescription docsToGroup, SearchSettings searchSettings,
                DocGroupSettings groupSettings, DocGroupSortSettings groupSortSettings) {
            super(param, JobDocsGrouped.class, docsToGroup, searchSettings);
            this.groupSettings = groupSettings;
            this.groupSortSettings = groupSortSettings;
        }

        @Override
        public DocGroupSettings getDocGroupSettings() {
            return groupSettings;
        }

        @Override
        public DocGroupSortSettings getDocGroupSortSettings() {
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
            return "docs";
        }

    }

    private DocGroups groups;

    public JobDocsGrouped(SearchManager searchMan, User user, JobDescription par) throws BlsException {
        super(searchMan, user, par);
    }

    @Override
    protected void performSearch() throws BlsException {
        docResults = ((JobWithDocs) inputJob).getDocResults();
        setPausedInternal();
        DocGroupSettings groupSett = jobDesc.getDocGroupSettings();
        DocGroups theGroups = docResults.groupedBy(groupSett.groupBy(), -1);
        DocGroupSortSettings sortSett = jobDesc.getDocGroupSortSettings();
        if (sortSett != null)
            theGroups = theGroups.sortedBy(sortSett.sortBy());

        groups = theGroups; // we're done, caller can use the groups now
    }

    /**
     * Get the grouped documents, or null if not available yet, or if no
     * sortSettings were provided by the JobDesc.
     * 
     * @return the grouped document results.
     */
    public DocGroups getGroups() {
        return groups;
    }

    @Override
    protected void dataStreamSubclassEntries(DataStream ds) {
        ds.entry("numberOfDocResults", docResults == null ? -1 : docResults.size())
                .entry("numberOfGroups", groups == null ? -1 : groups.size());
    }

    @Override
    protected void cleanup() {
        groups = null;
        super.cleanup();
    }
}
