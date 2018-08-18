package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.resultproperty.DocProperty;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

public class SearchDocsSorted extends SearchDocs {

    private SearchDocs docsSearch;

    private DocProperty sortBy;

    public SearchDocsSorted(QueryInfo queryInfo, SearchDocs docsSearch, DocProperty sortBy) {
        super(queryInfo);
        this.docsSearch = docsSearch;
        this.sortBy = sortBy;
    }

    @Override
    public DocResults execute() throws InvalidQuery {
        return docsSearch.execute().sortedBy(sortBy);
    }

}
