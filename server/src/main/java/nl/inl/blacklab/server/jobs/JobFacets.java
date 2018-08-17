package nl.inl.blacklab.server.jobs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.Pausible;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents a hits search and sort operation.
 */
public class JobFacets extends Job {

    public static class JobDescFacets extends JobDescription {

        private List<DocProperty> facets;

        public JobDescFacets(SearchParameters param, JobDescription docsToFacet, SearchSettings searchSettings,
                List<DocProperty> facets) {
            super(param, JobFacets.class, docsToFacet, searchSettings);
            this.facets = facets;
        }

        @Override
        public List<DocProperty> getFacets() {
            return facets;
        }

        @Override
        public String uniqueIdentifier() {
            StringBuilder strFacets = new StringBuilder();
            for (DocProperty facet : facets) {
                if (strFacets.length() > 0)
                    strFacets.append(", ");
                strFacets.append(facet.serialize());
            }
            return super.uniqueIdentifier() + "[" + facets + "])";
        }

        @Override
        public void dataStreamEntries(DataStream ds) {
            super.dataStreamEntries(ds);
            ds.entry("facets", facets);
        }

        @Override
        public String getUrlPath() {
            return "docs";
        }

    }

    private Map<String, DocGroups> counts;

    private DocResults docResults;

    public JobFacets(SearchManager searchMan, User user, JobDescription par) throws BlsException {
        super(searchMan, user, par);
    }

    @Override
    protected void performSearch() throws BlsException {
        // Now, group the docs according to the requested facets.
        docResults = ((JobWithDocs) inputJob).getDocResults();
        List<DocProperty> props = jobDesc.getFacets();

        Map<String, DocGroups> theCounts = new HashMap<>();
        for (DocProperty facetBy : props) {
            DocGroups facetCounts = docResults.groupedBy(facetBy, -1); //TODO: don't store all the results!
            theCounts.put(facetBy.getName(), facetCounts);
        }
        counts = theCounts; // we're done, caller can use the groups now
    }

    public Map<String, DocGroups> getCounts() {
        return counts;
    }

    public DocResults getDocResults() {
        return docResults;
    }

    @Override
    protected void dataStreamSubclassEntries(DataStream ds) {
        ds.entry("numberOfDocResults", docResults == null ? -1 : docResults.size())
                .entry("numberOfFacets", counts == null ? -1 : counts.size());
    }

    @Override
    protected void cleanup() {
        counts = null;
        docResults = null;
        super.cleanup();
    }

    @Override
    protected Pausible getObjectToPrioritize() {
        return docResults == null ? null : docResults.threadPauser();
    }

}
