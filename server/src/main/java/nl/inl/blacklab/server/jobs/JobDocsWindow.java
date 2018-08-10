package nl.inl.blacklab.server.jobs;

import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.DocResultsWindow;
import nl.inl.blacklab.server.datastream.DataStream;
import nl.inl.blacklab.server.exceptions.BlsException;
import nl.inl.blacklab.server.requesthandlers.SearchParameters;
import nl.inl.blacklab.server.search.SearchManager;

/**
 * Represents searching for a window in a larger set of hits.
 */
public class JobDocsWindow extends JobWithDocs {

    public static class JobDescDocsWindow extends JobDescription {

        WindowSettings windowSettings;

        public JobDescDocsWindow(SearchParameters param, JobDescription inputDesc, SearchSettings searchSettings,
                WindowSettings windowSettings) {
            super(param, JobDocsWindow.class, inputDesc, searchSettings);
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
            ds.entry("windowSettings", windowSettings);
        }

        @Override
        public String getUrlPath() {
            return "docs";
        }

    }

    private int requestedWindowSize;

    public JobDocsWindow(SearchManager searchMan, User user, JobDescription par) throws BlsException {
        super(searchMan, user, par);
    }

    @Override
    protected void performSearch() throws BlsException {
        // Now, create a HitsWindow on these hits.
        DocResults sourceResults = ((JobWithDocs) inputJob).getDocResults();
        setPausedInternal(); // make sure sourceResults has the right priority
        WindowSettings windowSett = jobDesc.getWindowSettings();
        int first = windowSett.first();
        requestedWindowSize = windowSett.size();
        if (!sourceResults.sizeAtLeast(first + 1)) {
            debug(logger, "Parameter first (" + first + ") out of range; setting to 0");
            first = 0;
        }
        // NOTE: this.docResults must be instanceof DocResultsWindow inside this class
        this.docResults = sourceResults.window(first, requestedWindowSize);
    }

    @Override
    public DocResultsWindow getDocResults() {
        return (DocResultsWindow) docResults;
    }

    @Override
    protected void dataStreamSubclassEntries(DataStream ds) {
        DocResultsWindow window = getDocResults();
        ds.entry("requestedWindowSize", requestedWindowSize)
                .entry("actualWindowSize", window == null ? -1 : window.size());
    }
}
