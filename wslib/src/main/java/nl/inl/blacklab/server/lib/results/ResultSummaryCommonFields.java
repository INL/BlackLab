package nl.inl.blacklab.server.lib.results;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import nl.inl.blacklab.search.lucene.MatchInfo;
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
    private List<MatchInfo.Def> matchInfoDefs;
    private ResultGroups<?> groups;
    private WindowStats window;
    private final String searchField;
    private final Collection<String> otherFields;

    ResultSummaryCommonFields(WebserviceParams searchParam, Index.IndexStatus indexStatus,
            SearchTimings timings, List<MatchInfo.Def> matchInfoDefs,
            ResultGroups<?> groups, WindowStats window, String searchField, Collection<String> otherFields) {
        this.searchParam = searchParam;
        if (searchParam.hasPattern())
            this.textPattern = searchParam.pattern().orElse(null);
        this.indexStatus = indexStatus;
        this.timings = timings;
        this.matchInfoDefs = matchInfoDefs == null ? Collections.emptyList() : matchInfoDefs;
        this.groups = groups;
        this.window = window;
        this.searchField = searchField;
        this.otherFields = otherFields;
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

    public List<MatchInfo.Def> getMatchInfoDefs() {
        return matchInfoDefs;
    }

    public ResultGroups<?> getGroups() {
        return groups;
    }

    public WindowStats getWindow() {
        return window;
    }

    public String getSearchField() {
        return searchField;
    }

    public Collection<String> getOtherFields() {
        return otherFields;
    }
}
