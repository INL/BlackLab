package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.HitsWindow;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents searching for a window in a larger set of hits.
 */
public class JobHitsWindow extends JobWithHits {

	public static class JobDescHitsWindow extends JobDescription {

		WindowSettings windowSettings;

		public JobDescHitsWindow(SearchParameters param, JobDescription inputDesc, SearchSettings searchSettings, WindowSettings windowSettings) {
			super(param, JobHitsWindow.class, inputDesc, searchSettings);
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

		@Override
		public String getUrlPath() {
			return "hits";
		}

	}

	private int requestedWindowSize;

	public JobHitsWindow(SearchManager searchMan, User user, JobDescription par) throws BlsException {
		super(searchMan, user, par);
	}

	@Override
	protected void performSearch() throws BlsException {
		// Now, create a HitsWindow on these hits.
		Hits inputHits = ((JobWithHits)inputJob).getHits();
		setPriorityInternal(); // make sure hits has the right priority
		WindowSettings windowSett = jobDesc.getWindowSettings();
		int first = windowSett.first();
		requestedWindowSize = windowSett.size();
		if (!inputHits.sizeAtLeast(first + 1)) {
			debug(logger, "Parameter first (" + first + ") out of range; setting to 0");
			first = 0;
		}
		// NOTE: this.hits must be instanceof HitsWindow inside this class
		this.hits = inputHits.window(first, requestedWindowSize);
		setPriorityInternal(); // make sure hits has the right priority
	}

	@Override
	public HitsWindow getHits() {
		return (HitsWindow) hits;
	}

	@Override
	protected void dataStreamSubclassEntries(DataStream ds) {
		HitsWindow hitsWindow = getHits();

		ds	.entry("requestedWindowSize", requestedWindowSize)
			.entry("actualWindowSize", hitsWindow == null ? -1 : hitsWindow.size());
        if (hitsWindow != null) {
            Hits hits = hitsWindow.getOriginalHits();
            ds  .entry("hitsObjId", hits.getHitsObjId())
                .entry("retrievedSoFar", hits.countSoFarHitsRetrieved())
                .entry("doneFetchingHits", hits.doneFetchingHits());
        }
	}
}
