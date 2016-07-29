package nl.inl.blacklab.server.search;

import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.HitsWindow;
import nl.inl.blacklab.server.dataobject.DataObject;
import nl.inl.blacklab.server.dataobject.DataObjectMapElement;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.util.ThreadPriority.Level;

/**
 * Represents searching for a window in a larger set of hits.
 */
public class JobHitsWindow extends Job {

	public static class JobDescHitsWindow extends JobDescription {

		WindowSettings windowSettings;

		ContextSettings contextSettings;

		public JobDescHitsWindow(JobDescription inputDesc, WindowSettings windowSettings, ContextSettings contextSettings) {
			super(inputDesc);
			this.windowSettings = windowSettings;
			this.contextSettings = contextSettings;
		}

		@Override
		public WindowSettings getWindowSettings() {
			return windowSettings;
		}

		@Override
		public ContextSettings getContextSettings() {
			return contextSettings;
		}

		@Override
		public String uniqueIdentifier() {
			return "JDHitsWindow [" + inputDesc + ", " + windowSettings + ", " + contextSettings + "]";
		}

		@Override
		public Job createJob(SearchManager searchMan, User user) throws BlsException {
			return new JobHitsWindow(searchMan, user, this);
		}

		@Override
		public DataObject toDataObject() {
			DataObjectMapElement o = new DataObjectMapElement();
			o.put("jobClass", "JobHitsWindow");
			o.put("inputDesc", inputDesc.toDataObject());
			o.put("windowSettings", windowSettings.toString());
			o.put("contextSettings", contextSettings.toString());
			return o;
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
		ContextSettings contextSett = jobDesc.getContextSettings();
		hitsWindow.settings().setContextSize(contextSett.size());
		hitsWindow.settings().setConcordanceType(contextSett.concType());
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
	public DataObjectMapElement toDataObject(boolean debugInfo) throws BlsException {
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
