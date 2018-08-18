package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

/**
 * Search operation that yields collocations.
 */
public class SearchCollocationsSampled extends SearchCollocations {

    private SearchCollocations source;
    
    private SampleParameters sampleParameters;

    public SearchCollocationsSampled(QueryInfo queryInfo, SearchCollocations source, SampleParameters sampleParameters) {
        super(queryInfo);
        this.source = source;
        this.sampleParameters = sampleParameters;
    }

    @Override
    public TermFrequencyList execute() throws InvalidQuery {
        return source.execute().sample(sampleParameters);
    }

}
