package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.RegexpTooLarge;
import nl.inl.blacklab.exceptions.WildcardTermTooBroad;
import nl.inl.blacklab.search.results.Hits;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.blacklab.search.results.SampleParameters;

/** A search that yields hits. */
public class SearchHitsSampled extends SearchHits {

    private SearchHits source;
    private SampleParameters sampleParameters;

    SearchHitsSampled(QueryInfo queryInfo, List<SearchOperation> ops, SearchHits source, SampleParameters sampleParameters) {
        super(queryInfo, ops);
        this.source = source;
        this.sampleParameters = sampleParameters;
    }
    
    @Override
    public Hits execute() throws WildcardTermTooBroad, RegexpTooLarge {
        return performCustom(source.execute().sample(sampleParameters));
    }

    @Override
    public SearchHitsSampled custom(SearchOperation operation) {
        return new SearchHitsSampled(queryInfo(), extraCustomOp(operation), source, sampleParameters);
    }
}
