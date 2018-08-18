package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.results.DocGroups;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

/** A search that yields groups of docs. */
public class SearchDocGroupsSampled extends SearchDocGroups {
    
    private SearchDocGroups source;

    private SampleParameters sampleParameters;

    public SearchDocGroupsSampled(QueryInfo queryInfo, List<SearchResultObserver> ops, SearchDocGroups source, SampleParameters sampleParameters) {
        super(queryInfo, ops);
        this.source = source;
        this.sampleParameters = sampleParameters;
    }
    
    @Override
    public DocGroups execute() throws InvalidQuery {
        return notifyObservers(source.execute().sample(sampleParameters));
    }

    @Override
    public SearchDocGroupsSampled observe(SearchResultObserver operation) {
        return new SearchDocGroupsSampled(queryInfo(), extraObserver(operation), source, sampleParameters);
    }
}
