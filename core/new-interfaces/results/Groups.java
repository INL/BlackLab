package nl.inl.blacklab.interfaces.results;

import java.util.function.Predicate;

/**
 * A set of results separated into groups based on some criterium.
 * 
 * @param <Result> type of result that's grouped 
 * @param <ResultGroup> type of the groups
 */
public interface Groups<Result, ResultGroup> extends Results<ResultGroup> {

    // Specific to Groups<Result>
    //-----------------------------------------
    
    /**
     * Get a specific result from this instance.
     * 
     * Only works for random access instances.
     * 
     * The returned result may be ephemeral. If you wish to store it, call 
     * Result.save() to get an immutable copy.
     * 
     * @param identity group identity
     * @param index result index to get (0-based)
     * @return the result
     */
    ResultGroup get(ResultPropertyValue identity);

    
    // Inherited from Results<Group<Result>>
    //-----------------------------------------
    
    @Override
    Groups<Result, ResultGroup> withRandomAccess();
    
    @Override
    default Groups<Result, ResultGroup> save() {
        // random-access Results are (effectively) immutable,
        // sequential ones are not
        return withRandomAccess();
    }
    
    @Override
    ResultGroup get(int index);
    
    @Override
    Groups<Result, ResultGroup> window(int first, int number);
    
    @Override
    Groups<Result, ResultGroup> sortedBy(ResultProperty<ResultGroup> sort, boolean reverse);
    
    @Override
    Groups<Result, ResultGroup> filteredBy(Predicate<ResultGroup> test);
    
    @Override
    Groups<Result, ResultGroup> sample(SampleParameters amount);
    
    @Override
    Groups<? extends ResultGroup, ? extends Group<ResultGroup>> groupedBy(ResultProperty<ResultGroup> criteria, int maxResultsToGatherPerGroup);
    
}
