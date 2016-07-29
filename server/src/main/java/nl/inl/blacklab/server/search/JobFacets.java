package nl.inl.blacklab.server.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.perdocument.DocCounts;
import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a hits search and sort operation.
 */
public class JobFacets extends Job {

	public static class JobDescFacets extends JobDescriptionBasic {

		JobDescription inputDesc;

		private List<DocProperty> facets;

		public JobDescFacets(String indexName, JobDescription docsToFacet, List<DocProperty> facets) {
			super(indexName);
			this.inputDesc = docsToFacet;
			this.facets = facets;
		}

		public JobDescription getInputDesc() {
			return inputDesc;
		}

		@Override
		public List<DocProperty> getFacets() {
			return facets;
		}

		@Override
		public String uniqueIdentifier() {
			return "JDFacets[" + indexName + ", " + inputDesc + ", " + facets + "]";
		}

		@Override
		public Job createJob(SearchManager searchMan, User user) throws BlsException {
			return new JobFacets(searchMan, user, this);
		}

		@Override
		public DataObject toDataObject() {
			DataObjectMapElement o = new DataObjectMapElement();
			o.put("jobClass", "JobDocsFacets");
			o.put("indexName", indexName);
			o.put("inputDesc", inputDesc.toDataObject());
			o.put("facets", facets.toString());
			return o;
		}

	}

	private Map<String, DocCounts> counts;

	private DocResults docResults;

	public JobFacets(SearchManager searchMan, User user, JobDescFacets par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		JobDescFacets facetDesc = (JobDescFacets)jobDesc;

		// First, execute blocking docs search.
		JobWithDocs docsSearch = (JobWithDocs) searchMan.search(user, facetDesc.getInputDesc());
		try {
			waitForJobToFinish(docsSearch);

			// Now, group the docs according to the requested facets.
			docResults = docsSearch.getDocResults();
		} finally {
			docsSearch.decrRef();
			docsSearch = null;
		}
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
