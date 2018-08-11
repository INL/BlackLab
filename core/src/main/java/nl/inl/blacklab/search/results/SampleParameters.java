package nl.inl.blacklab.search.results;

import java.util.Random;

/**
 * Parameters that determine how much to sample and whether to use a fixed seed.
 */
public class SampleParameters {
    
    private static final Random random = new Random(); 
    
    public static SampleParameters percentage(float percentageOfHits, long seedValueSet) {
        return new SampleParameters(true, percentageOfHits, 0, true, seedValueSet);
    }
    
    public static SampleParameters percentage(float percentageOfHits) {
        return new SampleParameters(true, percentageOfHits, 0, false, 0);
    }
    
    public static SampleParameters fixedNumber(int numberOfHitsSet, long seedValueSet) {
        return new SampleParameters(false, 0, numberOfHitsSet, true, seedValueSet);
    }
    
    public static SampleParameters fixedNumber(int numberOfHitsSet) {
        return new SampleParameters(false, 0, numberOfHitsSet, false, 0);
    }
    
    boolean isPercentage;
    
    private float percentageOfHits;
    
    private int numberOfHitsSet;
    
    private boolean isFixedSeed;
    
    private long seedValueSet;
    
    SampleParameters(boolean isPercentage, float percentageOfHits, int numberOfHitsSet, boolean isFixedSeed, long seedValueSet) {
        this.isPercentage = isPercentage;
        this.percentageOfHits = percentageOfHits;
        this.numberOfHitsSet = numberOfHitsSet;
        this.isFixedSeed = isFixedSeed;
        this.seedValueSet = seedValueSet;
        if (isPercentage && percentageOfHits < 0 || percentageOfHits > 1)
            throw new IllegalArgumentException("Sample percentage must be in the range 0-1");
        if (!isPercentage && numberOfHitsSet < 0)
            throw new IllegalArgumentException("Negative sample number specified");
    }
    
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
    long seed() {
        return isFixedSeed() ? seedValueSet() : random.nextLong(); 
    }
    
    /**
     * Get number of hits to sample, given total number of hits.
     * 
     * Always valid, regardless of isPercentage() setting.
     * 
     * @param totalNumberOfHits the total number of hits
     * @return number to sample
     */
    int numberOfHits(int totalNumberOfHits) {
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
    public boolean isPercentage() {
        return isPercentage;
    }
    
    /**
     * Get percentage of hits to sample.
     * 
     * Only valid if isRelative() return true.
     * 
     * @return desired percentage
     */
    public float percentageOfHits() {
        return percentageOfHits;
    }
    
    /**
     * Get number of hits to sample.
     * 
     * Only valid if isRelative() return false.
     * 
     * @return desired number
     */
    public int numberOfHitsSet() {
        return numberOfHitsSet;
    }
    
    /**
     * Was a fixed seed set?
     * 
     * If not, a random seed will be chosen each time I call {@link #seed()}.
     * 
     * @return true if a fixed seed value was set, false if it wasn't
     */
    public boolean isFixedSeed() {
        return isFixedSeed;
    }
    
    /**
     * What seed value was set?
     * 
     * Only valid if {@link #isFixedSeed()} returns true
     * 
     * @return fixed seed value
     */
    public long seedValueSet() {
        return seedValueSet;
    }
    
}
