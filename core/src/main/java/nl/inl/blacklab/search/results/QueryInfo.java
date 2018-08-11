package nl.inl.blacklab.search.results;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * Information about the original query.
 */
public final class QueryInfo {
    
    public static QueryInfo create(BlackLabIndex index) {
        return new QueryInfo(index, null, null, null);
    }
    
    public static QueryInfo create(BlackLabIndex index, AnnotatedField field) {
        return new QueryInfo(index, field, null, null);
    }
    
    public static QueryInfo create(BlackLabIndex index, AnnotatedField field, MaxSettings settings) {
        return new QueryInfo(index, field, settings, null);
    }
    
    public static QueryInfo create(BlackLabIndex index, AnnotatedField field, MaxSettings settings, MaxStats maxStats) {
        return new QueryInfo(index, field, settings, maxStats);
    }
    
    private BlackLabIndex index;

    /**
     * The field these hits came from (will also be used as concordance field)
     */
    private AnnotatedField field;
    
    /**
     * Settings for retrieving hits.
     */
    private MaxSettings settings;
    
    /**
     * Whether or not we exceed the max. hits to process/count.
     */
    private MaxStats maxStats;

    private QueryInfo(BlackLabIndex index, AnnotatedField field, MaxSettings settings, MaxStats maxStats) {
        super();
        this.index = index;
        this.field = field == null ? index.mainAnnotatedField() : field;
        this.settings = settings == null ? index.maxSettings() : settings;
        this.maxStats = maxStats == null ? new MaxStats() : maxStats;
    }

    /**
     * Returns the searcher object.
     *
     * @return the searcher object.
     */
    public BlackLabIndex index() {
        return index;
    }

    public AnnotatedField field() {
        return field;
    }

    public MaxSettings maxSettings() {
        return settings;
    }

    /**
     * Get whether or not the process/count limits were reached for the original query.
     * 
     * @return max stats
     */
    public MaxStats maxStats() {
        return maxStats;
    }
}