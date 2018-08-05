package nl.inl.blacklab.interfaces.results;

import java.util.Random;

/**
 * Parameters that determine how much to sample and whether to use a fixed seed.
 */
public interface SampleParameters {

    // "Simple" interface
    // -----------------------------------------------------------------------------
    // Always returns a seed and a number of hits to sample, regardless of settings.
    
    /**
     * Get the seed for the sample operation.
     * 
     * This may either be a fixed seed set by the user, or a randomly chosen seed.
     * 
     * @return seed
     */
    default long seed() {
        return isFixedSeed() ? seedValueSet() : (new Random()).nextLong(); 
    }
    
    /**
     * Get number of hits to sample, given total number of hits.
     * 
     * Always valid, regardless of isPercentage() setting.
     * 
     * @param totalNumberOfHits the total number of hits
     * @return number to sample
     */
    default int numberOfHits(int totalNumberOfHits) {
        return isPercentage() ? Math.round(percentageOfHits() * totalNumberOfHits) : numberOfHitsSet();
    }
    
    
    // More detailed interface
    // -----------------------------------------------------------------------------
    // Details about what type of sampling (percentage/number) was set, and if a fixed
    // seed value was set
    
    /**
     * Is this a relative sample amount?
     * 
     * If true, percentage() return the percentage of hits to sample.
     * 
     * If false, number() returns the absolute number of hits to sample.
     * 
     * @return true if this is relative, false if not
     */
    boolean isPercentage();
    
    /**
     * Get percentage of hits to sample.
     * 
     * Only valid if isRelative() return true.
     * 
     * @return desired percentage
     */
    float percentageOfHits();
    
    /**
     * Get number of hits to sample.
     * 
     * Only valid if isRelative() return false.
     * 
     * @return desired number
     */
    int numberOfHitsSet();
    
    /**
     * Was a fixed seed set?
     * 
     * If not, a random seed will be chosen each time I call {@link #seed()}.
     * 
     * @return true if a fixed seed value was set, false if it wasn't
     */
    boolean isFixedSeed();
    
    /**
     * What seed value was set?
     * 
     * Only valid if {@link #isFixedSeed()} returns true
     * 
     * @return fixed seed value
     */
    long seedValueSet();
    
}
