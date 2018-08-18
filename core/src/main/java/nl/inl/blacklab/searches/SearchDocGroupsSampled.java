package nl.inl.blacklab.searches;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

/** A search that yields groups of docs. */
public class SearchDocGroupsSampled extends SearchDocGroups {
    
    private SearchDocGroups source;

    private SampleParameters sampleParameters;

    public SearchDocGroupsSampled(QueryInfo queryInfo, SearchDocGroups source, SampleParameters sampleParameters) {
        super(queryInfo);
        this.source = source;
        this.sampleParameters = sampleParameters;
    }
    
    @Override
    public DocGroups execute() throws InvalidQuery {
        return source.execute().sample(sampleParameters);
    }
}
