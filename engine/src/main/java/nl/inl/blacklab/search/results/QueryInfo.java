package nl.inl.blacklab.search.results;

import nl.inl.blacklab.requestlogging.LogLevel;
import nl.inl.blacklab.requestlogging.SearchLogger;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * Information about the original query.
 */
public final class QueryInfo {

    public static QueryInfo create(BlackLabIndex index) {
        return create(index, (AnnotatedField)null, true, (SearchLogger)null);
    }

    public static QueryInfo create(BlackLabIndex index, AnnotatedField field) {
        return create(index, field, true, (SearchLogger)null);
    }

    public static QueryInfo create(BlackLabIndex index, AnnotatedField field, boolean useCache) {
        return create(index, field, useCache, (SearchLogger)null);
    }

    public static QueryInfo create(BlackLabIndex index, AnnotatedField field, boolean useCache, SearchLogger searchLogger) {
        return new QueryInfo(index, field, useCache, searchLogger);
    }

    private BlackLabIndex index;

    /** The field these hits came from (will also be used as concordance field) */
    private AnnotatedField field;

    /** Should we use the cache for this query, or bypass it? */
    private boolean useCache;

    /** Where we can log details about how the search is executed, or null to skip this logging (or once the search is done) */
    private SearchLogger searchLogger;

    private QueryInfo(BlackLabIndex index, AnnotatedField field, boolean useCache, SearchLogger searchLogger) {
        super();
        this.index = index;
        this.field = field == null ? index.mainAnnotatedField() : field;
        this.useCache = useCache;
        this.searchLogger = searchLogger;
    }

    /**
     * Return a copy with a different index.
     *
     * If this is the same index, simply returns this object.
     *
     * @param newIndex index to use
     * @return QueryInfo with the specified index
     */
    public QueryInfo withIndex(BlackLabIndex newIndex) {
        if (this.index == newIndex)
            return this;
        return new QueryInfo(newIndex, field, useCache, searchLogger);
    }

    /**
     * Log to the configured search logger, if any.
     *
     * @param level log level
     * @param msg message to log
     */
    public void log(LogLevel level, String msg) {
        if (searchLogger != null)
            searchLogger.log(level, msg);
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

    public SearchLogger searchLogger() {
        return searchLogger;
    }

}

