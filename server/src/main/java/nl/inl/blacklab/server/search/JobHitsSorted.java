package nl.inl.blacklab.server.search;

import org.apache.lucene.search.Query;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a hits search and sort operation.
 */
public class JobHitsSorted extends JobWithHits {

	public JobHitsSorted(SearchManager searchMan, User user, Description par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// First, execute blocking hits search.
		Description parNoSort = DescriptionImpl.jobHits(JobHits.class, jobDesc.getIndexName(), jobDesc.getPattern(),
				jobDesc.getFilterQuery(), null, jobDesc.getMaxSettings(), jobDesc.getSampleSettings(), jobDesc.getWindowSettings(),
				jobDesc.getContextSettings());
		JobWithHits hitsSearch = searchMan.searchHits(user, parNoSort);
		Hits hitsUnsorted;
		try {
			waitForJobToFinish(hitsSearch);

			// Now, sort the hits.
			hitsUnsorted = hitsSearch.getHits();
		} finally {
			hitsSearch.decrRef();
			hitsSearch = null;
		}
		HitsSortSettings sortSett = jobDesc.hitsSortSettings();
		HitProperty sortProp = HitProperty.deserialize(hits, sortSett.sortBy());
		/*if (sortProp == null)
			throw new QueryException("UNKNOWN_SORT_PROPERTY", "Unknown sort property '" + sortBy + "'.");
		*/
		if (sortProp != null) {
			// Be lenient of clients passing wrong sortBy values,
			// e.g. trying to sort a per-document search by hit context.
			// The problem is that applications might remember your
			// preferred sort and pass it with subsequent searches, even
			// if that particular sort cannot be performed on that type of search.
			// We don't want the client to have to validate this, so we simply
			// ignore sort requests we can't carry out.
			hits = hitsUnsorted.sortedBy(sortProp, sortSett.reverse());
		} else
			hits = hitsUnsorted;
		setPriorityInternal();
	}

	@Override
	protected void setPriorityInternal() {
		if (hits != null)
			setHitsPriority(hits);
	}

	@Override
	public Level getPriorityOfResultsObject() {
		return hits == null ? Level.RUNNING : hits.getPriorityLevel();
	}

	@Override
	public DataObjectMapElement toDataObject(boolean debugInfo) throws BlsException {
		DataObjectMapElement d = super.toDataObject(debugInfo);
		d.put("numberOfHits", hits == null ? -1 : hits.size());
		return d;
	}

	public static Description description(String indexName, TextPattern pattern, Query filterQuery,
			HitsSortSettings hitsSortSett, MaxSettings maxSettings, SampleSettings sampleSettings) {
		return DescriptionImpl.jobHits(JobHitsSorted.class, indexName, pattern, filterQuery, hitsSortSett, maxSettings, sampleSettings, null, null);
	}

}
