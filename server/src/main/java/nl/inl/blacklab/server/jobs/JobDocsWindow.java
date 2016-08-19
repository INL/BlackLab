package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.perdocument.DocResultsWindow;
import nl.inl.blacklab.search.Prioritizable;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents searching for a window in a larger set of hits.
 */
public class JobDocsWindow extends Job {

	public static class JobDescDocsWindow extends JobDescription {

		WindowSettings windowSettings;

		public JobDescDocsWindow(JobDescription inputDesc, WindowSettings windowSettings) {
			super(JobDocsWindow.class, inputDesc);
			this.windowSettings = windowSettings;
		}

		@Override
		public WindowSettings getWindowSettings() {
			return windowSettings;
		}

		@Override
		public String uniqueIdentifier() {
			return super.uniqueIdentifier() + windowSettings + ")";
		}

		@Override
		public void dataStreamEntries(DataStream ds) {
			super.dataStreamEntries(ds);
			ds	.entry("windowSettings", windowSettings);
		}

	}

	private DocResults sourceResults;

	private DocResultsWindow window;

	private int requestedWindowSize;

	public JobDocsWindow(SearchManager searchMan, User user, JobDescription par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// Now, create a HitsWindow on these hits.
		sourceResults = ((JobWithDocs)inputJob).getDocResults();
		setPriorityInternal(); // make sure sourceResults has the right priority
		WindowSettings windowSett = jobDesc.getWindowSettings();
		int first = windowSett.first();
		requestedWindowSize = windowSett.size();
		if (!sourceResults.sizeAtLeast(first + 1)) {
			debug(logger, "Parameter first (" + first + ") out of range; setting to 0");
			first = 0;
		}
		window = sourceResults.window(first, requestedWindowSize);
	}

	public DocResultsWindow getWindow() {
		return window;
	}

	@Override
	protected void dataStreamSubclassEntries(DataStream ds) {
		ds	.entry("requestedWindowSize", requestedWindowSize)
			.entry("actualWindowSize", window == null ? -1 : window.size());
	}

	@Override
	protected void cleanup() {
		window = null;
		super.cleanup();
	}

	@Override
	protected Prioritizable getObjectToPrioritize() {
		return sourceResults;
	}

}
