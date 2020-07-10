package nl.inl.blacklab.config;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.lucene.optimize.ClauseCombinerNfa;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.SearchSettings;

public class BLConfigSearch {
    BLConfigCollator collator = new BLConfigCollator();
    
    int contextSize = 5;
    
    int maxHitsToRetrieve = 5_000_000;
    
    int maxHitsToCount = 10_000_000;
    
    long fiMatchFactor = ClauseCombinerNfa.DEFAULT_NFA_THRESHOLD;

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

    public int getMaxHitsToRetrieve() {
        return maxHitsToRetrieve;
    }

    public void setMaxHitsToRetrieve(int maxHitsToRetrieve) {
        this.maxHitsToRetrieve = maxHitsToRetrieve;
    }

    public int getMaxHitsToCount() {
        return maxHitsToCount;
    }

    public void setMaxHitsToCount(int maxHitsToCount) {
        this.maxHitsToCount = maxHitsToCount;
    }

    public long getFiMatchFactor() {
        return fiMatchFactor;
    }

    public void setFiMatchFactor(int fiMatchFactor) {
        this.fiMatchFactor = fiMatchFactor;
    }

    public void apply(BlackLabIndex index) {
        index.setCollator(getCollator().get());
        index.setDefaultContextSize(ContextSize.get(getContextSize()));
        int maxHitsToProcess = getMaxHitsToRetrieve();
        int maxHitsToCount = getMaxHitsToCount();
        long fiMatchFactor = getFiMatchFactor();
        SearchSettings sett = SearchSettings.get(maxHitsToProcess, maxHitsToCount, fiMatchFactor);
        index.setSearchSettings(sett);
    }
}