package nl.inl.blacklab.search.lucene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Provides per-hit query-wide context, such as captured groups.
 *
 * This object is passed to the whole BLSpans tree before iterating over the
 * hits. Captured groups will register themselves here and receive an index in
 * the captured group array, and BLSpans objects that need access to captured
 * groups will store a reference to this context and use it later.
 */
public class HitQueryContext {

    /** Root of the BLSpans tree for this query. */
    private BLSpans rootSpans;

    /** Match info names for our query, in index order */
    List<String> matchInfoNames = new ArrayList<>();

    /** If non-null, this part of the query searches a different field (parallel corpora) */
    private final String overriddenField;

    public HitQueryContext(BLSpans spans) {
        this(spans, null);
    }

    private HitQueryContext(BLSpans spans, String overriddenField) {
        this.rootSpans = spans;
        this.overriddenField = overriddenField;
    }

    public HitQueryContext withSpans(BLSpans spans) {
        HitQueryContext result = new HitQueryContext(spans, overriddenField);
        result.matchInfoNames = matchInfoNames;
        return result;
    }

    public HitQueryContext withField(String overriddenField) {
        HitQueryContext result = this;
        if (overriddenField != null) {
            result = new HitQueryContext(rootSpans, overriddenField);
            result.matchInfoNames = matchInfoNames;
        }
        return result;
    }

    /**
     * Set our Spans object.
     *
     * Used when manually iterating through the index segments, because we go
     * through several Spans for a single query.
     *
     * @param spans our new spans
     */
    public void setSpans(BLSpans spans) {
        this.rootSpans = spans;
    }

    /**
     * Register a match info (e.g. captured group), assigning it a unique index number.
     *
     * @param name the group's name
     * @return the group's assigned index
     */
    public int registerMatchInfo(String name) {
        while (matchInfoNames.contains(name)) {
            return matchInfoNames.indexOf(name); // already registered, reuse
        }
        matchInfoNames.add(name);
        return matchInfoNames.size() - 1; // index in array
    }

    /**
     * Get the number of captured groups
     * 
     * @return number of captured groups
     */
    public int numberOfMatchInfos() {
        return matchInfoNames.size();
    }

    /**
     * Retrieve all the captured group information.
     *
     * Used by Hits.
     *
     * @param matchInfo array to place the captured group information into
     */
    public void getMatchInfo(MatchInfo[] matchInfo) {
        rootSpans.getMatchInfo(matchInfo);
    }

    /**
     * Get the names of the captured groups, in index order.
     *
     * @return the list of names
     */
    public List<String> getMatchInfoNames() {
        return Collections.unmodifiableList(matchInfoNames);
    }

    /**
     * Get the overridden field for this part of the query (if any).
     *
     * Used for parallel corpora. Will always return null in non-parallel corpora.
     *
     * @return the field this part of the query searches, or null if it's just the main field we're querying
     */
    public String getOverriddenField() {
        return overriddenField;
    }
}
