package nl.inl.blacklab.server.jobs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.perdocument.DocCounts;
import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.SearchManager;
import nl.inl.util.ThreadPriority.Level;

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
			return super.uniqueIdentifier() + "[[" + facets + "]]";
		}

		@Override
		public DataObjectMapElement toDataObject() {
			DataObjectMapElement o = super.toDataObject();
			o.put("facets", facets.toString());
			return o;
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
			counts.put(facetBy.serialize(), facetCounts);
		}
		counts = theCounts; // we're done, caller can use the groups now
	}

	@Override
	protected void setPriorityInternal() {
		if (docResults != null)
			setDocsPriority(docResults);
	}

	@Override
	public Level getPriorityOfResultsObject() {
		return docResults == null ? Level.RUNNING : docResults.getPriorityLevel();
	}

	public Map<String, DocCounts> getCounts() {
		return counts;
	}

	public DocResults getDocResults() {
		return docResults;
	}

	@Override
	public DataObjectMapElement toDataObject(boolean debugInfo) throws BlsException {
		DataObjectMapElement d = super.toDataObject(debugInfo);
		d.put("numberOfDocResults", docResults == null ? -1 : docResults.size());
		d.put("numberOfFacets", counts == null ? -1 : counts.size());
		return d;
	}

	@Override
	protected void cleanup() {
		counts = null;
		docResults = null;
		super.cleanup();
	}

}
