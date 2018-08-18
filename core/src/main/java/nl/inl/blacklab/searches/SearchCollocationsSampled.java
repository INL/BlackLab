package nl.inl.blacklab.searches;

import java.util.List;

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

    public SearchCollocationsSampled(QueryInfo queryInfo, List<SearchResultObserver> ops, SearchCollocations source, SampleParameters sampleParameters) {
        super(queryInfo, ops);
        this.source = source;
        this.sampleParameters = sampleParameters;
    }

    @Override
    public TermFrequencyList execute() throws InvalidQuery {
        return notifyObservers(source.execute().sample(sampleParameters));
    }

    @Override
    public SearchCollocationsSampled observe(SearchResultObserver observer) {
        return new SearchCollocationsSampled(queryInfo(), extraObserver(observer), source, sampleParameters);
    }

}
