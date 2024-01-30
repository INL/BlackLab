package nl.inl.blacklab.search.results;

import java.util.Collections;
import java.util.List;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * Information about the original query.
 */
public final class QueryInfo {

    public static QueryInfo create(BlackLabIndex index) {
        return create(index, null, true);
    }

    public static QueryInfo create(BlackLabIndex index, AnnotatedField field) {
        return create(index, field, true);
    }

    public static QueryInfo create(BlackLabIndex index, AnnotatedField field, boolean useCache) {
        return new QueryInfo(index, field, useCache);
    }

    private final BlackLabIndex index;

    /** The field these hits came from (will also be used as concordance field) */
    private final AnnotatedField field;

    /** Should we use the cache for this query, or bypass it? */
    private final boolean useCache;

    /** The list of match info names registered while executing the query. These are all possible match infos that
     *  could be captured, although not every hit is guaranteed to have every match info. */
    private List<String> matchInfoNames;

    /** How long executing certain parts of the operation took. */
    private final QueryTimings timings = new QueryTimings();

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

    /** Get timings objects.
     *
     * Describes how long executing certain parts of the operation took.
     * Mostly useful for debugging.
     *
     * @return the query timings object
     */
    public QueryTimings timings() {
        return timings;
    }

    /**
     * Get match info names.
     *
     * Returns the match info names registered while executing the query. These are all possible match infos that
     * could be captured, although not every hit is guaranteed to have every match info.
     *
     * @return the list of match info names
     */
    public List<String> matchInfoNames() {
        return Collections.unmodifiableList(matchInfoNames);
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

    public void setMatchInfoNames(List<String> matchInfoNames) {
        this.matchInfoNames = matchInfoNames;
    }
}

