package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsWindow extends SearchDocs {

    private SearchDocs docsSearch;

    private int first;

    private int number;

    public SearchDocsWindow(QueryInfo queryInfo, List<SearchResultObserver> customOperations, SearchDocs docsSearch, int first, int number) {
        super(queryInfo, customOperations);
        this.docsSearch = docsSearch;
        this.first = first;
        this.number = number;
    }

    @Override
    public DocResults execute() throws InvalidQuery {
        return notifyObservers(docsSearch.execute().window(first, number));
    }

    @Override
    public SearchDocsWindow observe(SearchResultObserver operation) {
        return new SearchDocsWindow(queryInfo(), extraObserver(operation), docsSearch, first, number);
    }

}
