package nl.inl.blacklab.interfaces.results;

import java.util.List;
import java.util.function.Predicate;

import nl.inl.blacklab.interfaces.ContextSize;
import nl.inl.blacklab.interfaces.MatchSensitivity;
import nl.inl.blacklab.interfaces.struct.Annotation;
import nl.inl.blacklab.search.TermFrequencyList;

/**
 * Main interface for dealing with the individual hits resulting from
 * a query.
 * 
 * Can be implemented as a thin shell around Spans or around another Hits object,
 * or as a class that stores the information about all its hits (e.g. when sorting 
 * hits).
 * 
 * Comes in two flavours: sequential or random access. Random access instances allow you
 * to access any hit at any time, while sequential instances only allow sequential
 * access.
 * 
 * Random access instances are immutable. Sequential instances are not, with respect to 
 * their hits: their mutable state is the hits they have already seen and cannot go back to.
 * 
 * A sequential instance is said to be pristine if none of its hits have been seen;
 * it is said to be exhausted if all of its hits have been seen, or the maximum number
 * of hits to process was reached.
 */
public interface Hits extends Results<Hit> {
    
    // Specific to Hits
    //-----------------------------------------
	
	ResultsStatsHitsDocs stats();
	
	@Override
    default ResultsNumber size() {
	    return stats().hits().processed();
	}
	
    /**
     * Count occurrences of context words around hit.
     *
     * NOTE: Must be called on a pristine-sequential or random access instance.
     * Calling this will exhaust a sequential instance.
     * 
     * @param annotation the property to use for the collocations (must have a forward index)
     * @param size context size to use for determining collocations
     * @param sensitivity sensitivity settings
     * @return the frequency of each occurring token
     */
    TermFrequencyList collocations(Annotation annotation, ContextSize size, MatchSensitivity sensitivity);
    
    // Captured groups
    // ===============================================================
    // Programs need to know whether or not groups were captured, and
    // what their names are.
    
    /**
     * Get the list of captured group names.
     * @return list of captured group names or null if no groups were captured
     */
    public List<String> capturedGroupNames();

    /**
     * Were groups captured for this query?
     * @return true if they were, false if not
     */
    default boolean hasCapturedGroups() {
        return capturedGroupNames() != null;
    }
    
	
    // Inherited from Results<Hit>
    //-----------------------------------------
    
    @Override
    Hits withRandomAccess();
    
    @Override
    default Hits save() {
        // random-access Results are (effectively) immutable,
        // sequential ones are not
        return withRandomAccess();
    }
    
    @Override
    Hits window(int first, int number);
    
    @Override
    Hits sortedBy(ResultProperty<Hit> sortBy, boolean reverse);
    
    @Override
    Hits filteredBy(Predicate<Hit> test);
    
    @Override
    Hits sample(SampleParameters amount);
    
    @Override
    HitGroups groupedBy(ResultProperty<Hit> groupBy, int maxResultsToGatherPerGroup);

	
}
