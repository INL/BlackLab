package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Search operation that yields collocations.
 */
public class SearchCollocationsFromHits extends SearchCollocations {

    private SearchHits source;
    private Annotation annotation;
    private ContextSize contextSize;
    private MatchSensitivity sensitivity;

    public SearchCollocationsFromHits(QueryInfo queryInfo, List<SearchResultObserver> ops, SearchHits source, Annotation annotation, ContextSize contextSize, MatchSensitivity sensitivity) {
        super(queryInfo, ops);
        this.source = source;
        this.annotation = annotation;
        this.contextSize = contextSize;
        this.sensitivity = sensitivity;
    }

    @Override
    public TermFrequencyList execute() throws InvalidQuery {
        return notifyObservers(source.execute().collocations(annotation, contextSize, sensitivity));
    }

    @Override
    public SearchCollocationsFromHits observe(SearchResultObserver op) {
        return new SearchCollocationsFromHits(queryInfo(), extraObserver(op), source, annotation, contextSize, sensitivity);
    }

}
