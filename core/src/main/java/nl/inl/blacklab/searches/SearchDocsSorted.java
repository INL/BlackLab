package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsSorted extends SearchDocs {

    private SearchDocs docsSearch;

    private DocProperty sortBy;

    public SearchDocsSorted(QueryInfo queryInfo, List<SearchResultObserver> customOperations, SearchDocs docsSearch, DocProperty sortBy) {
        super(queryInfo, customOperations);
        this.docsSearch = docsSearch;
        this.sortBy = sortBy;
    }

    @Override
    public DocResults execute() throws InvalidQuery {
        return notifyObservers(docsSearch.execute().sortedBy(sortBy));
    }

    @Override
    public SearchDocsSorted observe(SearchResultObserver operation) {
        return new SearchDocsSorted(queryInfo(), extraObserver(operation), docsSearch, sortBy);
    }

}
