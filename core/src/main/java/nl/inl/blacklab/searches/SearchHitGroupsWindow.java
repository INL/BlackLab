package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A search operation that yields groups of hits.
 */
public class SearchHitGroupsWindow extends SearchHitGroups {
    
    private SearchHitGroups source;
    private int first;
    private int number;

    public SearchHitGroupsWindow(QueryInfo queryInfo, List<SearchResultObserver> ops, SearchHitGroups source, int first, int number) {
        super(queryInfo, ops);
        this.source = source;
        this.first = first;
        this.number = number;
    }

    @Override
    public HitGroups execute() throws InvalidQuery {
        return notifyObservers(source.execute().window(first, number));
    }

    @Override
    public SearchHitGroupsWindow observe(SearchResultObserver operation) {
        return new SearchHitGroupsWindow(queryInfo(), extraObserver(operation), source, first, number);
    }
}
