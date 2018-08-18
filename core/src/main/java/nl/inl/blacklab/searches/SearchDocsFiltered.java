package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.resultproperty.PropertyValue;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsFiltered extends SearchDocs {

    private SearchDocs docsSearch;

    private DocProperty property;

    private PropertyValue value;

    public SearchDocsFiltered(QueryInfo queryInfo, SearchDocs docsSearch, DocProperty sortBy, PropertyValue value) {
        super(queryInfo);
        this.docsSearch = docsSearch;
        this.property = sortBy;
        this.value = value;
    }

    @Override
    public DocResults execute() throws InvalidQuery {
        return docsSearch.execute().filteredBy(property, value);
    }

}
