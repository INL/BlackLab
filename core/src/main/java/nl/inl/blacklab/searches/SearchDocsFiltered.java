package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsFiltered extends SearchDocs {

    private SearchDocs docsSearch;

    private DocProperty property;

    private PropertyValue value;

    public SearchDocsFiltered(QueryInfo queryInfo, List<SearchResultObserver> customOperations, SearchDocs docsSearch, DocProperty sortBy, PropertyValue value) {
        super(queryInfo, customOperations);
        this.docsSearch = docsSearch;
        this.property = sortBy;
        this.value = value;
    }

    @Override
    public DocResults execute() throws InvalidQuery {
        return notifyObservers(docsSearch.execute().filteredBy(property, value));
    }

    @Override
    public SearchDocsFiltered observe(SearchResultObserver operation) {
        return new SearchDocsFiltered(queryInfo(), extraObserver(operation), docsSearch, property, value);
    }

}
