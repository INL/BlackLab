package nl.inl.blacklab.server.search;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents a hits search and sort operation.
 */
public class JobHitsSorted extends JobWithHits {

	public JobHitsSorted(SearchManager searchMan, User user, SearchParameters par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// First, execute blocking hits search.
		SearchParameters parNoSort = par.copyWithout("sort");
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
		String sortBy = par.getString("sort");
		if (sortBy == null)
			sortBy = "";
		boolean reverse = false;
		if (sortBy.length() > 0 && sortBy.charAt(0) == '-') {
			reverse = true;
			sortBy = sortBy.substring(1);
		}
		HitProperty sortProp = HitProperty.deserialize(hitsUnsorted, sortBy);
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
			hits = hitsUnsorted.sortedBy(sortProp, reverse);
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
	public DataObjectMapElement toDataObject(boolean debugInfo) {
		DataObjectMapElement d = super.toDataObject(debugInfo);
		d.put("numberOfHits", hits == null ? -1 : hits.size());
		return d;
	}

}
