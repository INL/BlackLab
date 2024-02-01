package nl.inl.blacklab.search.lucene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
    List<MatchInfo.Def> matchInfoDefs = new ArrayList<>();

    /** Default field for this query (the primary field we search in; or only field for non-parallel corpora) */
    private final String defaultField;

    /** If non-null, this part of the query searches a different field (parallel corpora) */
    private final String overriddenField;

    public HitQueryContext(BLSpans spans, String defaultField) {
        this(spans, defaultField, null);
    }

    private HitQueryContext(BLSpans spans, String defaultField, String overriddenField) {
        this.rootSpans = spans;
        this.defaultField = defaultField;
        this.overriddenField = overriddenField;
    }

    boolean fieldWasOverridden() {
        return getOverriddenField() != null && !getOverriddenField().equals(getDefaultField());
    }

    public HitQueryContext withSpans(BLSpans spans) {
        HitQueryContext result = new HitQueryContext(spans, defaultField, overriddenField);
        result.matchInfoDefs = matchInfoDefs;
        return result;
    }

    public HitQueryContext withField(String overriddenField) {
        HitQueryContext result = this;
        if (overriddenField != null) {
            result = new HitQueryContext(rootSpans, defaultField, overriddenField);
            result.matchInfoDefs = matchInfoDefs;
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
     * @param type the group's type, or null if we don't know here (i.e. when referring to the group as a span)
     * @return the group's assigned index
     */
    public int registerMatchInfo(String name, MatchInfo.Type type) {
        return registerMatchInfo(name, type, null);
    }

    /**
     * Register a match info (e.g. captured group), assigning it a unique index number.
     *
     * @param name the group's name
     * @param type the group's type, or null if we don't know here (i.e. when referring to the group as a span)
     * @param actualField if null, use the (possibly overridden) field from this context.
     *                    Otherwise use the field specified here. Used e.g. when capturing relation, which should always
     *                    be captured in the source field, even if the span mode is target (and the context reflects that).
     * @return the group's assigned index
     */
    public int registerMatchInfo(String name, MatchInfo.Type type, String actualField) {
        Optional<MatchInfo.Def> mi = matchInfoDefs.stream()
                .filter(mid -> mid.getName().equals(name))
                .findFirst();
        if (mi.isPresent()) {
            mi.get().updateType(type); // update type (e.g. if group is referred to before we know its type)
            return mi.get().getIndex(); // already registered, reuse
        }
        String field = overriddenField;
        if (actualField != null) {
            // Use a different field than this context specifies.
            // (e.g. always capture relations in source field even if span mode is target)
            field = actualField.equals(defaultField) ? null : actualField;
        }
        MatchInfo.Def newMatchInfo = new MatchInfo.Def(matchInfoDefs.size(), name, type, field);
        matchInfoDefs.add(newMatchInfo);
        return newMatchInfo.getIndex(); // index in array
    }

    /**
     * Get the number of captured groups
     * 
     * @return number of captured groups
     */
    public int numberOfMatchInfos() {
        return matchInfoDefs.size();
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
     * Get the match infos definitions.
     *
     * The list is in index order.
     *
     * @return the list of match infos
     */
    public List<MatchInfo.Def> getMatchInfoDefs() {
        return Collections.unmodifiableList(matchInfoDefs);
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

    public String getDefaultField() {
        return defaultField;
    }
}
