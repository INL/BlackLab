package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields groups of docs. */
public class SearchDocGroupsSorted extends SearchDocGroups {
    
    private SearchDocGroups source;

    private DocGroupProperty property;

    public SearchDocGroupsSorted(QueryInfo queryInfo, SearchDocGroups source, DocGroupProperty property) {
        super(queryInfo);
        this.source = source;
        this.property = property;
    }
    
    @Override
    public DocGroups execute() throws InvalidQuery {
        return source.execute().sortedBy(property);
    }
}
