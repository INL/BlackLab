package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocResults;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

public class SearchDocsSampled extends SearchDocs {

    private SearchDocs docsSearch;

    private SampleParameters sampleParameters;

    public SearchDocsSampled(QueryInfo queryInfo, SearchDocs docsSearch, SampleParameters sampleParameters) {
        super(queryInfo);
        this.docsSearch = docsSearch;
        this.sampleParameters = sampleParameters;
    }

    @Override
    public DocResults execute() throws InvalidQuery {
        return docsSearch.execute().sample(sampleParameters);
    }

}
