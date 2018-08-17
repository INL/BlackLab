package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;

/** A search that produces DocResults. */
public abstract class SearchDocs extends AbstractSearch {

    public SearchDocs(QueryInfo queryInfo, List<SearchOperation> customOperations) {
        super(queryInfo, customOperations);
    }

    @Override
    public abstract DocResults execute() throws InvalidQuery;

}
