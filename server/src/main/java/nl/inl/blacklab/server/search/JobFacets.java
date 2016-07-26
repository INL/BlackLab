package nl.inl.blacklab.server.search;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.perdocument.DocCounts;
import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a hits search and sort operation.
 */
public class JobFacets extends Job {

	private Map<String, DocCounts> counts;

	private DocResults docResults;

	public JobFacets(SearchManager searchMan, User user, Description par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// First, execute blocking docs search.
		Description parNoGroup = DescriptionImpl.jobDocs(JobDocs.class, searchMan, jobDesc.getIndexName(), jobDesc.getPattern(),
				jobDesc.getFilterQuery(), null, jobDesc.getMaxSettings(), jobDesc.getWindowSettings(), jobDesc.getContextSettings());
		JobWithDocs docsSearch = searchMan.searchDocs(user, parNoGroup);
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

	public static Description description(SearchManager searchMan, String indexName, TextPattern pattern, Query filterQuery, List<DocProperty> facets, MaxSettings maxSettings) {
		return DescriptionImpl.facets(JobFacets.class, searchMan, indexName, pattern, filterQuery, facets, maxSettings);
	}

}
