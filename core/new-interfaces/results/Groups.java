package nl.inl.blacklab.interfaces.results;

import java.util.function.Predicate;

/**
 * A set of results separated into groups based on some criterium.
 * 
 * @param <T> type of result that's grouped 
 * @param <G> type of the groups
 */
public interface Groups<T, G> extends Results<G> {

    // Specific to Groups<T>
    //-----------------------------------------
    
    /**
     * Get a specific result from this instance.
     * 
     * Only works for random access instances.
     * 
     * The returned result may be ephemeral. If you wish to store it, call 
     * T.save() to get an immutable copy.
     * 
     * @param identity group identity
     * @param index result index to get (0-based)
     * @return the result
     */
    G get(ResultPropertyValue identity);

    
    // Inherited from Results<Group<T>>
    //-----------------------------------------
    
    @Override
    Groups<T, G> withRandomAccess();
    
    @Override
    default Groups<T, G> save() {
        // random-access Results are (effectively) immutable,
        // sequential ones are not
        return withRandomAccess();
    }
    
    @Override
    G get(int index);
    
    @Override
    Groups<T, G> window(int first, int number);
    
    @Override
    Groups<T, G> sortedBy(ResultProperty<G> sort, boolean reverse);
    
    @Override
    Groups<T, G> filteredBy(Predicate<G> test);
    
    @Override
    Groups<T, G> sample(SampleParameters amount);
    
    @Override
    Groups<? extends G, ? extends Group<G>> groupedBy(ResultProperty<G> criteria, int maxResultsToGatherPerGroup);
    
}
