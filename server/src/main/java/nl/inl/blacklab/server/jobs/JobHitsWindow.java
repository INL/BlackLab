package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.HitsWindow;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents searching for a window in a larger set of hits.
 */
public class JobHitsWindow extends Job {

	public static class JobDescHitsWindow extends JobDescription {

		WindowSettings windowSettings;

		public JobDescHitsWindow(JobDescription inputDesc, WindowSettings windowSettings) {
			super(JobHitsWindow.class, inputDesc);
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

	private HitsWindow hitsWindow;

	private int requestedWindowSize;

	public JobHitsWindow(SearchManager searchMan, User user, JobDescription par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	public void performSearch() throws BlsException {
		// Now, create a HitsWindow on these hits.
		Hits hits = ((JobWithHits)inputJob).getHits();
		setPriorityInternal(); // make sure hits has the right priority
		WindowSettings windowSett = jobDesc.getWindowSettings();
		int first = windowSett.first();
		requestedWindowSize = windowSett.size();
		if (!hits.sizeAtLeast(first + 1)) {
			debug(logger, "Parameter first (" + first + ") out of range; setting to 0");
			first = 0;
		}
		hitsWindow = hits.window(first, requestedWindowSize);
		setPriorityInternal(); // make sure hits has the right priority
	}

	public HitsWindow getWindow() {
		return hitsWindow;
	}

	@Override
	protected void dataStreamSubclassEntries(DataStream ds) {
		ds	.entry("requestedWindowSize", requestedWindowSize)
			.entry("actualWindowSize", hitsWindow == null ? -1 : hitsWindow.size());
	}

	@Override
	protected void cleanup() {
		hitsWindow = null;
		super.cleanup();
	}

	@Override
	protected HitsWindow getObjectToPrioritize() {
		return hitsWindow;
	}

}
