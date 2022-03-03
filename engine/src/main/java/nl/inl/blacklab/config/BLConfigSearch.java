package nl.inl.blacklab.config;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.SearchSettings;

public class BLConfigSearch {
    BLConfigCollator collator = new BLConfigCollator();
    
    int contextSize = 5;
    
    long maxHitsToRetrieve = 5_000_000;

    long maxHitsToCount = 10_000_000;
    
    long fiMatchFactor = ClauseCombinerNfa.defaultForwardIndexMatchingThreshold;

    public BLConfigCollator getCollator() {
        return collator;
    }

    public void setCollator(BLConfigCollator collator) {
        this.collator = collator;
    }

    public int getContextSize() {
        return contextSize;
    }

    public void setContextSize(int contextSize) {
        this.contextSize = contextSize;
    }

    public long getMaxHitsToRetrieve() {
        return maxHitsToRetrieve;
    }

    public void setMaxHitsToRetrieve(int maxHitsToRetrieve) {
        this.maxHitsToRetrieve = maxHitsToRetrieve;
    }

    public long getMaxHitsToCount() {
        return maxHitsToCount;
    }

    public void setMaxHitsToCount(long maxHitsToCount) {
        this.maxHitsToCount = maxHitsToCount;
    }

    public long getFiMatchFactor() {
        return fiMatchFactor;
    }

    public void setFiMatchFactor(int fiMatchFactor) {
        this.fiMatchFactor = fiMatchFactor;
    }

    /**
     * Apply the search configuration settings to an opened index.
     *
     * @param index index to apply the configuration to
     */
    public void apply(BlackLabIndex index) {
        index.setCollator(getCollator().get());
        index.setDefaultContextSize(ContextSize.get(getContextSize()));
        long maxHitsToProcess = getMaxHitsToRetrieve();
        long maxHitsToCount = getMaxHitsToCount();
        long fiMatchFactor = getFiMatchFactor();
        SearchSettings sett = SearchSettings.get(maxHitsToProcess, maxHitsToCount, fiMatchFactor);
        ClauseCombinerNfa.setDefaultForwardIndexMatchingThreshold(fiMatchFactor);
        index.setSearchSettings(sett);
    }
}
