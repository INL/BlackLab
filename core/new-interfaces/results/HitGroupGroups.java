package nl.inl.blacklab.interfaces.results;

import java.util.function.Predicate;

/** Groups of hits that have been grouped again.
 *
 * Example: if you group hits by document, that produces groups of hits.
 * If you then group those documents (those groups of hits) by the author
 * of the document, that produces groups of documents by the same author.
 * Each of those groups contains a number of documents, and each of those documents
 * contains a number of hits.
 * 
 * It is possible to keep grouping deeper and deeper, with no real limit, but there 
 * are no more conveniently named classes for deeper levels, so the generic types 
 * might look a bit crazy.
 */
public interface HitGroupGroups extends Groups<HitGroup, Group<HitGroup>> {
    
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
    Group<HitGroup> get(ResultPropertyValue identity);

    
    // Inherited from Results<Group<Result>>
    //-----------------------------------------
    
    @Override
    HitGroupGroups withRandomAccess();
    
    @Override
    default HitGroupGroups save() {
        // random-access Results are (effectively) immutable,
        // sequential ones are not
        return withRandomAccess();
    }
    
    @Override
    Group<HitGroup> get(int index);
    
    @Override
    HitGroupGroups window(int first, int number);
    
    @Override
    HitGroupGroups sortedBy(ResultProperty<Group<HitGroup>> sort, boolean reverse);
    
    @Override
    HitGroupGroups filteredBy(Predicate<Group<HitGroup>> test);
    
    @Override
    HitGroupGroups sample(SampleParameters amount);
    
    @Override
    Groups<? extends Group<HitGroup>, ? extends Group<Group<HitGroup>>> groupedBy(ResultProperty<Group<HitGroup>> criteria, int maxResultsToGatherPerGroup);
}
