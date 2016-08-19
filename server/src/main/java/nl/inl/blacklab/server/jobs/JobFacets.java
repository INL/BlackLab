package nl.inl.blacklab.server.jobs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.perdocument.DocCounts;
import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents a hits search and sort operation.
 */
public class JobFacets extends Job {

	public static class JobDescFacets extends JobDescription {

		private List<DocProperty> facets;

		public JobDescFacets(JobDescription docsToFacet, List<DocProperty> facets) {
			super(JobFacets.class, docsToFacet);
			this.facets = facets;
		}

		@Override
		public List<DocProperty> getFacets() {
			return facets;
		}

		@Override
		public String uniqueIdentifier() {
			StringBuilder strFacets = new StringBuilder();
			for (DocProperty facet: facets) {
				if (strFacets.length() > 0)
					strFacets.append(", ");
				strFacets.append(facet.serialize());
			}
			return super.uniqueIdentifier() + "[" + facets + "])";
		}

		@Override
		public void dataStreamEntries(DataStream ds) {
			super.dataStreamEntries(ds);
			ds	.entry("facets", facets);
		}

	}

	private Map<String, DocCounts> counts;

	private DocResults docResults;

	public JobFacets(SearchManager searchMan, User user, JobDescription par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// Now, group the docs according to the requested facets.
		docResults = ((JobWithDocs)inputJob).getDocResults();
		List<DocProperty> props = jobDesc.getFacets();

		Map<String, DocCounts> theCounts = new HashMap<>();
		for (DocProperty facetBy: props) {
			DocCounts facetCounts = docResults.countBy(facetBy);
			theCounts.put(facetBy.getName(), facetCounts);
		}
		counts = theCounts; // we're done, caller can use the groups now
	}

	public Map<String, DocCounts> getCounts() {
		return counts;
	}

	public DocResults getDocResults() {
		return docResults;
	}

	@Override
	protected void dataStreamSubclassEntries(DataStream ds) {
		ds	.entry("numberOfDocResults", docResults == null ? -1 : docResults.size())
			.entry("numberOfFacets", counts == null ? -1 : counts.size());
	}

	@Override
	protected void cleanup() {
		counts = null;
		docResults = null;
		super.cleanup();
	}

	@Override
	protected DocResults getObjectToPrioritize() {
		return docResults;
	}

}
