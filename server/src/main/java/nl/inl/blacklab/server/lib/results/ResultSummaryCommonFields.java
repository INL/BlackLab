package nl.inl.blacklab.server.lib.results;

import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.SearchCreator;
import nl.inl.blacklab.server.lib.SearchTimings;

public class ResultSummaryCommonFields {
    private SearchCreator searchParam;
    private Index.IndexStatus indexStatus;
    private SearchTimings timings;
    private ResultGroups<?> groups;
    private WindowStats window;

    ResultSummaryCommonFields(SearchCreator searchParam, Index.IndexStatus indexStatus,
            SearchTimings timings,
            ResultGroups<?> groups, WindowStats window) {
        this.searchParam = searchParam;
        this.indexStatus = indexStatus;
        this.timings = timings;
        this.groups = groups;
        this.window = window;
    }

    public SearchCreator getSearchParam() {
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
