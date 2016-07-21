package nl.inl.blacklab.server.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.perdocument.DocCounts;
import nl.inl.blacklab.perdocument.DocProperty;
import nl.inl.blacklab.perdocument.DocPropertyMultiple;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a hits search and sort operation.
 */
public class JobFacets extends Job {

	private Map<String, DocCounts> counts;

	private DocResults docResults;

	public JobFacets(SearchManager searchMan, User user, SearchParameters par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// First, execute blocking docs search.
		SearchParameters parNoGroup = par.copyWithout("group", "sort");
		JobWithDocs docsSearch = searchMan.searchDocs(user, parNoGroup);
		try {
			waitForJobToFinish(docsSearch);

			// Now, group the docs according to the requested facets.
			docResults = docsSearch.getDocResults();
		} finally {
			docsSearch.decrRef();
			docsSearch = null;
		}
		String facets = par.getString("facets");
		if (facets == null) {
			// If no facets were specified, we shouldn't even be here.
			throw new RuntimeException("facets == null");
		}
		DocProperty propMultipleFacets = DocProperty.deserialize(facets);
		List<DocProperty> props = new ArrayList<>();
		if (propMultipleFacets instanceof DocPropertyMultiple) {
			// Multiple facets requested
			for (DocProperty prop: (DocPropertyMultiple)propMultipleFacets) {
				props.add(prop);
			}
		} else {
			// Just a single facet requested
			props.add(propMultipleFacets);
		}

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
	public DataObjectMapElement toDataObject(boolean debugInfo) {
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
