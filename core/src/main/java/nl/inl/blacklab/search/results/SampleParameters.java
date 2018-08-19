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
        this.seedValueSet = isFixedSeed ? seedValueSet : random.nextLong();
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
     * However, for an existing instance of SampleParameters, the seed won't ever change.
     * 
     * @return seed
     */
    public long seed() {
        return seedValueSet; 
    }
    
    /**
     * Get number of hits to sample, given total number of hits.
     * 
     * Always valid, regardless of isPercentage() setting.
     * 
     * @param totalNumberOfHits the total number of hits
     * @return number to sample
     */
    public int numberOfHits(int totalNumberOfHits) {
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
     * If not, a random seed was chosen. In either case, call {@link #seed()}
     * to get the seed used. The return value will not change for an instance
     * of SampleParameters.
     * 
     * @return true if a fixed seed value was set, false if it wasn't
     */
    public boolean isFixedSeed() {
        return isFixedSeed;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (isPercentage ? 1231 : 1237);
        if (isPercentage)
            result = prime * result + Float.floatToIntBits(percentageOfHits);
        else
            result = prime * result + numberOfHitsSet;
        result = prime * result + (int) (seedValueSet ^ (seedValueSet >>> 32));
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SampleParameters other = (SampleParameters) obj;
        if (isPercentage != other.isPercentage)
            return false;
        if (!isPercentage && numberOfHitsSet != other.numberOfHitsSet)
            return false;
        if (isPercentage && Float.floatToIntBits(percentageOfHits) != Float.floatToIntBits(other.percentageOfHits))
            return false;
        if (seedValueSet != other.seedValueSet)
            return false;
        return true;
    }
    
    @Override
    public String toString() {
        return "sample=" + (isPercentage ? String.format("%.1f%%", percentageOfHits) : Integer.toString(numberOfHitsSet)) + 
                ", seed=" + seedValueSet + (isFixedSeed ? " (FIXED)" : " (RANDOM)");
    }
    
}
