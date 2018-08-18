package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields groups of docs. */
public class SearchDocGroupsWindow extends SearchDocGroups {
    
    private SearchDocGroups source;
    private int first;
    private int number;

    public SearchDocGroupsWindow(QueryInfo queryInfo, SearchDocGroups source, int first, int number) {
        super(queryInfo);
        this.source = source;
        this.first = first;
        this.number = number;
    }
    
    @Override
    public DocGroups execute() throws InvalidQuery {
        return source.execute().window(first, number);
    }
}
