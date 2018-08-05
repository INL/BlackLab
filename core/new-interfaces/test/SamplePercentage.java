package nl.inl.blacklab.interfaces.test;

import nl.inl.blacklab.interfaces.results.SampleParameters;

/** Randomly sample ten percent of hits, no fixed seed */
final class SamplePercentage implements SampleParameters {

    public static SampleParameters get(float percentage) {
        return new SamplePercentage(percentage);
    }

    private float percentage;

    public SamplePercentage(float percentage) {
        this.percentage = percentage;
    }
    
    @Override
    public boolean isPercentage() {
        return true;
    }

    @Override
    public float percentageOfHits() {
        return percentage;
    }

    @Override
    public int numberOfHitsSet() {
        return 0;
    }

    @Override
    public boolean isFixedSeed() {
        return false;
    }

    @Override
    public long seedValueSet() {
        return 0;
    }
}