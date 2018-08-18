package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocGroupProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that yields groups of docs. */
public class SearchDocGroupsFiltered extends SearchDocGroups {
    
    private SearchDocGroups source;

    private DocGroupProperty property;
    
    private PropertyValue value;

    public SearchDocGroupsFiltered(QueryInfo queryInfo, List<SearchResultObserver> ops, SearchDocGroups source, DocGroupProperty property, PropertyValue value) {
        super(queryInfo, ops);
        this.source = source;
        this.property = property;
        this.value = value;
    }
    
    @Override
    public DocGroups execute() throws InvalidQuery {
        return notifyObservers(source.execute().sortedBy(property));
    }

    @Override
    public SearchDocGroupsFiltered observe(SearchResultObserver operation) {
        return new SearchDocGroupsFiltered(queryInfo(), extraObserver(operation), source, property, value);
    }
}
