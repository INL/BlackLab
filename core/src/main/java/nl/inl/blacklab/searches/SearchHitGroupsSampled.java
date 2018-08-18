package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.HitGroups;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

/**
 * A search operation that yields groups of hits.
 */
public class SearchHitGroupsSampled extends SearchHitGroups {
    
    private SearchHitGroups source;
    
    private SampleParameters sampleParameters;

    public SearchHitGroupsSampled(QueryInfo queryInfo, List<SearchResultObserver> ops, SearchHitGroups source, SampleParameters sampleParameters) {
        super(queryInfo, ops);
        this.source = source;
        this.sampleParameters = sampleParameters;
    }

    @Override
    public HitGroups execute() throws InvalidQuery {
        return notifyObservers(source.execute().sample(sampleParameters));
    }

    @Override
    public SearchHitGroupsSampled observe(SearchResultObserver operation) {
        return new SearchHitGroupsSampled(queryInfo(), extraObserver(operation), source, sampleParameters);
    }
}
