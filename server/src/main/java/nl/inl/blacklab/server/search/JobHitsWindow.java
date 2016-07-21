package nl.inl.blacklab.server.search;

import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.HitsWindow;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents searching for a window in a larger set of hits.
 */
public class JobHitsWindow extends Job {

	private HitsWindow hitsWindow;

	private int requestedWindowSize;

	public JobHitsWindow(SearchManager searchMan, User user, SearchParameters par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// First, execute blocking hits search.
		JobWithHits hitsSearch = searchMan.searchHits(user, par);
		try {
			waitForJobToFinish(hitsSearch);

			// Now, create a HitsWindow on these hits.
			Hits hits = hitsSearch.getHits();
			setPriorityInternal(); // make sure hits has the right priority
			int first = par.getInteger("first");
			requestedWindowSize = par.getInteger("number");
			if (!hits.sizeAtLeast(first + 1)) {
				debug(logger, "Parameter first (" + first + ") out of range; setting to 0");
				first = 0;
			}
			hitsWindow = hits.window(first, requestedWindowSize);
			setPriorityInternal(); // make sure hits has the right priority
			int contextSize = par.getInteger("wordsaroundhit");
			int maxContextSize = searchMan.getMaxContextSize();
			if (contextSize > maxContextSize) {
				debug(logger, "Clamping context size to " + maxContextSize + " (" + contextSize + " requested)");
				contextSize = maxContextSize;
			}
			hitsWindow.settings().setContextSize(contextSize);
			hitsWindow.settings().setConcordanceType(par.getString("usecontent").equals("orig") ? ConcordanceType.CONTENT_STORE : ConcordanceType.FORWARD_INDEX);
		} finally {
			hitsSearch.decrRef();
		}
	}

	public HitsWindow getWindow() {
		return hitsWindow;
	}

	@Override
	protected void setPriorityInternal() {
		setHitsPriority(hitsWindow);
	}

	@Override
	public Level getPriorityOfResultsObject() {
		return hitsWindow == null ? Level.RUNNING : hitsWindow.getPriorityLevel();
	}

	@Override
	public DataObjectMapElement toDataObject(boolean debugInfo) {
		DataObjectMapElement d = super.toDataObject(debugInfo);
		d.put("requestedWindowSize", requestedWindowSize);
		d.put("actualWindowSize", hitsWindow == null ? -1 : hitsWindow.size());
		return d;
	}

	@Override
	protected void cleanup() {
		hitsWindow = null;
		super.cleanup();
	}

}
