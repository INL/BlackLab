package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

public class SearchDocsSampled extends SearchDocs {

    private SearchDocs docsSearch;

    private SampleParameters sampleParameters;

    public SearchDocsSampled(QueryInfo queryInfo, List<SearchResultObserver> customOperations, SearchDocs docsSearch, SampleParameters sampleParameters) {
        super(queryInfo, customOperations);
        this.docsSearch = docsSearch;
        this.sampleParameters = sampleParameters;
    }

    @Override
    public DocResults execute() throws InvalidQuery {
        return notifyObservers(docsSearch.execute().sample(sampleParameters));
    }

    @Override
    public SearchDocsSampled observe(SearchResultObserver operation) {
        return new SearchDocsSampled(queryInfo(), extraObserver(operation), docsSearch, sampleParameters);
    }

}
