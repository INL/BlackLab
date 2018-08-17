package nl.inl.blacklab.searches;

import java.util.List;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.results.QueryInfo;

/**
 * Search operation that yields collocations.
 */
public abstract class SearchCollocations extends AbstractSearch {

    public SearchCollocations(QueryInfo queryInfo, List<SearchOperation> ops) {
        super(queryInfo, ops);
    }

    @Override
    public abstract TermFrequencyList execute() throws InvalidQuery;

    @Override
    public abstract SearchCollocations custom(SearchOperation receiver);

//    /**
//     * Sort hits.
//     * 
//     * @param sortBy what to sort by
//     * @return resulting operation
//     */
//    public abstract SearchCollocations sortBy(ResultProperty<TermFrequency> sortBy);
//
//    /**
//     * Sample hits.
//     * 
//     * @param par how many hits to sample; seed
//     * @return resulting operation
//     */
//    public abstract SearchCollocations sample(SampleParameters par);
//
//    /**
//     * Filter hits.
//     * 
//     * @param test what hits to keep
//     * @return resulting operation
//     */
//    public abstract SearchCollocations filter(Predicate<TermFrequency> test);
//
//    /**
//     * Get window of hits.
//     * 
//     * @param first first hit to select
//     * @param number number of hits to select
//     * @return resulting operation
//     */
//    public abstract SearchCollocations window(int first, int number);
//
//
}
