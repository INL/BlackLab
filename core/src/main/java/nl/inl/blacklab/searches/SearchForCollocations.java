package nl.inl.blacklab.searches;

import java.util.function.Predicate;

import nl.inl.blacklab.resultproperty.ResultProperty;
import nl.inl.blacklab.search.TermFrequency;
import nl.inl.blacklab.search.TermFrequencyList;
import nl.inl.blacklab.search.results.SampleParameters;

/**
 * Search operation that yields collocations.
 */
public abstract class SearchForCollocations extends SearchForResults<TermFrequency, TermFrequencyList> {

    @Override
    public abstract TermFrequencyList execute();

    @Override
    public abstract SearchForCollocations custom(SearchOperation<TermFrequencyList> receiver);

    /**
     * Sort hits.
     * 
     * @param sortBy what to sort by
     * @return resulting operation
     */
    @Override
    public abstract SearchForCollocations sortBy(ResultProperty<TermFrequency> sortBy);

    /**
     * Sample hits.
     * 
     * @param par how many hits to sample; seed
     * @return resulting operation
     */
    @Override
    public abstract SearchForCollocations sample(SampleParameters par);

    /**
     * Filter hits.
     * 
     * @param test what hits to keep
     * @return resulting operation
     */
    @Override
    public abstract SearchForCollocations filter(Predicate<TermFrequency> test);

    /**
     * Get window of hits.
     * 
     * @param first first hit to select
     * @param number number of hits to select
     * @return resulting operation
     */
    @Override
    public abstract SearchForCollocations window(int first, int number);


}
