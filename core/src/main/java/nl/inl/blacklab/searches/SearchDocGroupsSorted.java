package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields groups of docs. */
public class SearchDocGroupsSorted extends SearchDocGroups {
    
    private SearchDocGroups source;

    private DocGroupProperty property;

    public SearchDocGroupsSorted(QueryInfo queryInfo, List<SearchResultObserver> ops, SearchDocGroups source, DocGroupProperty property) {
        super(queryInfo, ops);
        this.source = source;
        this.property = property;
    }
    
    @Override
    public DocGroups execute() throws InvalidQuery {
        return notifyObservers(source.execute().sortedBy(property));
    }

    @Override
    public SearchDocGroupsSorted observe(SearchResultObserver operation) {
        return new SearchDocGroupsSorted(queryInfo(), extraObserver(operation), source, property);
    }
}
