package nl.inl.blacklab.searches;

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

    public SearchHitGroupsWindow(QueryInfo queryInfo, SearchHitGroups source, int first, int number) {
        super(queryInfo);
        this.source = source;
        this.first = first;
        this.number = number;
    }

    @Override
    public HitGroups execute() throws InvalidQuery {
        return source.execute().window(first, number);
    }
}
