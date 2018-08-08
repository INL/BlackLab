package nl.inl.blacklab.search.results;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.ConcordanceType;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Freezable;

public class HitsSettings implements Freezable {

    /** When setting how many hits to retrieve/count, this means "no limit". */
    private static final int UNLIMITED_HITS = -1;
    
    public static final int DEFAULT_MAX_RETRIEVE = 1000000;
    
    public static final int DEFAULT_MAX_COUNT = UNLIMITED_HITS;
    
    /** Annotated field name for default contents field */
    public static final String DEFAULT_CONTENTS_FIELD_NAME = "contents";
    
    private static final ConcordanceType DEFAULT_CONC_TYPE = ConcordanceType.CONTENT_STORE;
    
    private static final int DEFAULT_CONTEXT_SIZE = 5;

    /**
     * Stop retrieving hits after this number. (NO_LIMIT = -1 = don't stop
     * retrieving)
     */
    private int maxHitsToRetrieve;

    /**
     * Stop counting hits after this number. (NO_LIMIT = -1 = don't stop counting)
     */
    private int maxHitsToCount;

    /** What to use to make concordances: forward index or content store */
    private ConcordanceType concsType;

    /**
     * The default field to use for retrieving concordance information.
     */
    private AnnotatedField concordanceField;

    /** Our desired context size */
    private int desiredContextSize;

    /** Index object, so we can ask for the default contents field if none was set yet */
    private BlackLabIndex searcher;

    private boolean frozen;

    public HitsSettings(HitsSettings defaults) {
        concordanceField = defaults.concordanceField();
        maxHitsToRetrieve = defaults.maxHitsToRetrieve();
        maxHitsToCount = defaults.maxHitsToCount();
        concsType = defaults.concordanceType();
        desiredContextSize = defaults.contextSize();
    }

    public HitsSettings(BlackLabIndex searcher) {
        this.searcher = searcher;
        this.concordanceField = null; // later on we'll ask for the main contents field (when the index knows that)
        maxHitsToRetrieve = DEFAULT_MAX_RETRIEVE;
        maxHitsToCount = DEFAULT_MAX_COUNT;
        concsType = DEFAULT_CONC_TYPE;
        desiredContextSize = DEFAULT_CONTEXT_SIZE;
    }

    /** @return the maximum number of hits to retrieve. */
    public int maxHitsToRetrieve() {
        return maxHitsToRetrieve;
    }

    /** @return the maximum number of hits to count. */
    public int maxHitsToCount() {
        return maxHitsToCount;
    }

    /**
     * Are we making concordances using the forward index (true) or using the
     * content store (false)? Forward index is more efficient but returns
     * concordances that don't include XML tags.
     *
     * @return true iff we use the forward index for making concordances.
     */
    public ConcordanceType concordanceType() {
        return concsType;
    }

    /**
     * Do we want to retrieve concordances from the forward index or from the
     * content store? Forward index is more efficient but doesn't exactly reproduces
     * the original XML.
     *
     * The default type can be set by calling Searcher.setDefaultConcordanceType().
     *
     * @param type the type of concordances to make
     */
    public void setConcordanceType(ConcordanceType type) {
        this.concsType = type;
    }

    /**
     * Returns the field to use for retrieving concordances.
     *
     * @return the field name
     */
    public AnnotatedField concordanceField() {
        // If not set yet, ask the index for the default field
        if (concordanceField == null)
            concordanceField = searcher.metadata().annotatedFields().main();
        return concordanceField;
    }

    public int contextSize() {
        return desiredContextSize;
    }
    
    
    // Methods that mutate data
    //------------------------------------------------------------------------

    /**
     * Set the maximum number of hits to retrieve
     * 
     * @param n the number of hits, or HitsSettings.UNLIMITED for no limit
     */
    public void setMaxHitsToRetrieve(int n) {
        ensureNotFrozen();
        this.maxHitsToRetrieve = n;
    }

    /**
     * Set the maximum number of hits to count
     * 
     * @param n the number of hits, or HitsSettings.UNLIMITED for no limit
     */
    public void setMaxHitsToCount(int n) {
        ensureNotFrozen();
        this.maxHitsToCount = n;
    }

    /**
     * Sets the field to use for retrieving concordances.
     *
     * @param concordanceField the field 
     */
    public void setConcordanceField(AnnotatedField concordanceField) {
        ensureNotFrozen();
        this.concordanceField = concordanceField;
    }

    public void setContextSize(int n) {
        ensureNotFrozen();
        desiredContextSize = n;
    }

    @Override
    public void freeze() {
        this.frozen = true;
    }

    @Override
    public boolean isFrozen() {
        return frozen;
    }

}
