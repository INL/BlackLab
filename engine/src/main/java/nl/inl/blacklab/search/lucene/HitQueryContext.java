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

    /** Captured group names for our query, in index order */
    List<String> groupNames = new ArrayList<>();

    /** We use this to check if subclauses capture groups or not */
    private int numberOfTimesGroupRegistered = 0;

    public HitQueryContext(BLSpans spans) {
        this.rootSpans = spans;
    }

    public HitQueryContext() {
        this(null);
    }

    public HitQueryContext copyWith(BLSpans spans) {
        HitQueryContext result = new HitQueryContext(spans);
        result.groupNames = groupNames;
        result.numberOfTimesGroupRegistered = numberOfTimesGroupRegistered;
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
     * Register a captured group, assigning it a unique index number.
     *
     * @param name the group's name
     * @param deduplicate if true, make the group name unique if it already exists
     * @return the group's assigned index
     */
    public int registerCapturedGroup(String name, boolean deduplicate) {
        numberOfTimesGroupRegistered++;
        while (groupNames.contains(name)) {
            if (deduplicate) {
                // Ensure the group name is unique
                name += "_";
            } else {
                return groupNames.indexOf(name); // already registered, reuse
            }
        }
        groupNames.add(name);
        return groupNames.size() - 1; // index in array
    }

    /**
     * Register a captured group, assigning it a unique index number.
     *
     * If the group name already exists, the existing index number will be returned.
     *
     * @param name the group's name
     * @return the group's assigned index
     */
    public int registerCapturedGroup(String name) {
        return registerCapturedGroup(name, false);
    }

    /**
     * Get the number of captured groups
     * 
     * @return number of captured groups
     */
    public int numberOfCapturedGroups() {
        return groupNames.size();
    }

    /**
     * Retrieve all the captured group information.
     *
     * Used by Hits.
     *
     * @param capturedGroups array to place the captured group information into
     */
    public void getCapturedGroups(RelationInfo[] capturedGroups) {
        rootSpans.getCapturedGroups(capturedGroups);
    }

    /**
     * Get the names of the captured groups, in index order.
     *
     * @return the list of names
     */
    public List<String> getCapturedGroupNames() {
        return Collections.unmodifiableList(groupNames);
    }

    public int getCaptureRegisterNumber() {
        return numberOfTimesGroupRegistered;
    }
}
