package nl.inl.blacklab.server.lib.results;

import java.util.Collections;
import java.util.List;

import nl.inl.blacklab.search.results.ResultGroups;
import nl.inl.blacklab.search.results.WindowStats;
import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.server.index.Index;
import nl.inl.blacklab.server.lib.SearchTimings;
import nl.inl.blacklab.server.lib.WebserviceParams;

public class ResultSummaryCommonFields {
    private WebserviceParams searchParam;
    private TextPattern textPattern = null;
    private Index.IndexStatus indexStatus;
    private SearchTimings timings;
    private List<String> matchInfoNames;
    private ResultGroups<?> groups;
    private WindowStats window;

    ResultSummaryCommonFields(WebserviceParams searchParam, Index.IndexStatus indexStatus,
            SearchTimings timings, List<String> matchInfoNames,
            ResultGroups<?> groups, WindowStats window) {
        this.searchParam = searchParam;
        if (searchParam.hasPattern())
            this.textPattern = searchParam.pattern().orElse(null);
        this.indexStatus = indexStatus;
        this.timings = timings;
        this.matchInfoNames = matchInfoNames == null ? Collections.emptyList() : matchInfoNames;
        this.groups = groups;
        this.window = window;
    }

    public WebserviceParams getSearchParam() {
        return searchParam;
    }

    public TextPattern getTextPattern() {
        return textPattern;
    }

    public Index.IndexStatus getIndexStatus() {
        return indexStatus;
    }

    public SearchTimings getTimings() {
        return timings;
    }

    public List<String> getMatchInfoNames() {
        return matchInfoNames;
    }

    public ResultGroups<?> getGroups() {
        return groups;
    }

    public WindowStats getWindow() {
        return window;
    }
}
