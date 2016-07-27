package nl.inl.blacklab.server.search;

import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.perdocument.DocResultsWindow;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents searching for a window in a larger set of hits.
 */
public class JobDocsWindow extends Job {

	private DocResults sourceResults;

	private DocResultsWindow window;

	private int requestedWindowSize;

	public JobDocsWindow(SearchManager searchMan, User user, Description par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// First, execute blocking docs search.
		JobWithDocs docsSearch = (JobWithDocs) searchMan.search(user, jobDesc.docs());
		try {
			waitForJobToFinish(docsSearch);

			// Now, create a HitsWindow on these hits.
			sourceResults = docsSearch.getDocResults();
			setPriorityInternal(); // make sure sourceResults has the right priority
		} finally {
			docsSearch.decrRef();
			docsSearch = null;
		}
		WindowSettings windowSett = jobDesc.getWindowSettings();
		int first = windowSett.first();
		requestedWindowSize = windowSett.size();
		if (!sourceResults.sizeAtLeast(first + 1)) {
			debug(logger, "Parameter first (" + first + ") out of range; setting to 0");
			first = 0;
		}
		window = sourceResults.window(first, requestedWindowSize);
	}

	@Override
	protected void setPriorityInternal() {
		if (sourceResults != null)
			setDocsPriority(sourceResults);
	}

	@Override
	public Level getPriorityOfResultsObject() {
		return sourceResults == null ? Level.RUNNING : sourceResults.getPriorityLevel();
	}

	public DocResultsWindow getWindow() {
		return window;
	}

	@Override
	public DataObjectMapElement toDataObject(boolean debugInfo) throws BlsException {
		DataObjectMapElement d = super.toDataObject(debugInfo);
		d.put("requestedWindowSize", requestedWindowSize);
		d.put("actualWindowSize", window == null ? -1 : window.size());
		return d;
	}

	@Override
	protected void cleanup() {
		window = null;
		super.cleanup();
	}

}
