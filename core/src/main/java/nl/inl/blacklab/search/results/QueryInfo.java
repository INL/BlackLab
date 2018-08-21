package nl.inl.blacklab.search.results;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexImpl;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * Information about the original query.
 */
public final class QueryInfo {

    public static QueryInfo create(BlackLabIndex index) {
        return new QueryInfo(index, null, true);
    }

    public static QueryInfo create(BlackLabIndex index, AnnotatedField field) {
        return new QueryInfo(index, field, true);
    }

    public static QueryInfo create(BlackLabIndexImpl index, AnnotatedField field, boolean useCache) {
        return new QueryInfo(index, field, useCache);
    }

    private BlackLabIndex index;

    /** The field these hits came from (will also be used as concordance field) */
    private AnnotatedField field;

    /** The results object id of the original query (for debugging). */
    private int resultsObjectId = -1;

    /** Should we use the cache for this query, or bypass it? */
    private boolean useCache;

    private QueryInfo(BlackLabIndex index, AnnotatedField field, boolean useCache) {
        super();
        this.index = index;
        this.field = field == null ? index.mainAnnotatedField() : field;
        this.useCache = useCache;
    }

    /** @return the index that was searched. */
    public BlackLabIndex index() {
        return index;
    }

    /** @return field that was searched */
    public AnnotatedField field() {
        return field;
    }

    /** @return should we use the cache for this query, or bypass it? */
    public boolean useCache() {
        return useCache;
    }

    /** @return the results object id of the original query. */
    public int resultsObjectId() {
        return resultsObjectId;
    }

    /**
     * Set the results object id of the original query.
     * 
     * This is only done exactly once, by the original query as it's constructed.
     * Attempting to change it later throws an exception. It is only used for debugging.
     * 
     * @param resultsObjectId results object id
     * @throws UnsupportedOperationException if you attempt to set it again
     * 
     */
    public void setResultsObjectId(int resultsObjectId) {
        if (this.resultsObjectId != -1)
            throw new UnsupportedOperationException("Cannot change resultsObjectId");
        if (resultsObjectId == -1)
            throw new UnsupportedOperationException("Invalid resultsObjectId: -1");
        this.resultsObjectId = resultsObjectId;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((field == null) ? 0 : field.hashCode());
        result = prime * result + ((index == null) ? 0 : index.hashCode());
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
        QueryInfo other = (QueryInfo) obj;
        if (field == null) {
            if (other.field != null)
                return false;
        } else if (!field.equals(other.field))
            return false;
        if (index == null) {
            if (other.index != null)
                return false;
        } else if (!index.equals(other.index))
            return false;
        return true;
    }
    
    
}

