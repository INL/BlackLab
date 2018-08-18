package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsWindow extends SearchDocs {

    private SearchDocs docsSearch;

    private int first;

    private int number;

    public SearchDocsWindow(QueryInfo queryInfo, SearchDocs docsSearch, int first, int number) {
        super(queryInfo);
        this.docsSearch = docsSearch;
        this.first = first;
        this.number = number;
    }

    @Override
    public DocResults execute() throws InvalidQuery {
        return docsSearch.execute().window(first, number);
    }

}
