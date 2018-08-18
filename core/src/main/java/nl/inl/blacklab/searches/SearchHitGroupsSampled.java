package nl.inl.blacklab.searches;

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

    public SearchHitGroupsSampled(QueryInfo queryInfo, SearchHitGroups source, SampleParameters sampleParameters) {
        super(queryInfo);
        this.source = source;
        this.sampleParameters = sampleParameters;
    }

    @Override
    public HitGroups execute() throws InvalidQuery {
        return source.execute().sample(sampleParameters);
    }
}
