package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.HitGroupProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * A search operation that yields groups of hits.
 */
public class SearchHitGroupsFiltered extends SearchHitGroups {
    
    private SearchHitGroups source;
    
    private HitGroupProperty property;
    
    private PropertyValue value;

    public SearchHitGroupsFiltered(QueryInfo queryInfo, List<SearchResultObserver> ops, SearchHitGroups source, HitGroupProperty property, PropertyValue value) {
        super(queryInfo, ops);
        this.source = source;
        this.property = property;
        this.value = value;
    }

    @Override
    public HitGroups execute() throws InvalidQuery {
        return notifyObservers(source.execute().filteredBy(property, value));
    }

    @Override
    public SearchHitGroupsFiltered observe(SearchResultObserver operation) {
        return new SearchHitGroupsFiltered(queryInfo(), extraObserver(operation), source, property, value);
    }
}
