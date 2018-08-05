package nl.inl.blacklab.interfaces.results;

import java.util.function.Predicate;

/**
 * Groups of hits.
 * 
 * Two examples:
 * - hits grouped by their hit text
 * - hits grouped by documents (per-document results)
 */
public interface HitGroups extends Groups<Hit, HitGroup> {
    
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
    @Override
    HitGroup get(ResultPropertyValue identity);

    
    // Inherited from Results<Group<Result>>
    //-----------------------------------------
    
    @Override
    HitGroups withRandomAccess();
    
    @Override
    default HitGroups save() {
        // random-access Results are (effectively) immutable,
        // sequential ones are not
        return withRandomAccess();
    }
    
    @Override
    HitGroup get(int index);
    
    @Override
    HitGroups window(int first, int number);
    
    @Override
    HitGroups sortedBy(ResultProperty<HitGroup> sort, boolean reverse);
    
    @Override
    HitGroups filteredBy(Predicate<HitGroup> test);
    
    @Override
    HitGroups sample(SampleParameters amount);
    
    @Override
    HitGroupGroups groupedBy(ResultProperty<HitGroup> criteria, int maxResultsToGatherPerGroup);
}
