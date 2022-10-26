package nl.inl.blacklab.server.lib.results;

import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.WebserviceParams;
import nl.inl.blacklab.server.lib.SearchTimings;

public class ResultSummaryCommonFields {
    private WebserviceParams searchParam;
    private Index.IndexStatus indexStatus;
    private SearchTimings timings;
    private ResultGroups<?> groups;
    private WindowStats window;

    ResultSummaryCommonFields(WebserviceParams searchParam, Index.IndexStatus indexStatus,
            SearchTimings timings,
            ResultGroups<?> groups, WindowStats window) {
        this.searchParam = searchParam;
        this.indexStatus = indexStatus;
        this.timings = timings;
        this.groups = groups;
        this.window = window;
    }

    public WebserviceParams getSearchParam() {
        return searchParam;
    }

    public Index.IndexStatus getIndexStatus() {
        return indexStatus;
    }

    public SearchTimings getTimings() {
        return timings;
    }

    public ResultGroups<?> getGroups() {
        return groups;
    }

    public WindowStats getWindow() {
        return window;
    }
}
