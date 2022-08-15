package nl.inl.blacklab.search.results;

import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.Span;

/** Captured group information for a list of hits.
 *
 * This interface is read-only.
 */
public interface CapturedGroups {

    /**
     * Get the group names
     *
     * @return group names
     */
    List<String> names();

    /**
     * Get the captured groups.
     *
     * @param hit hit to get groups for
     * @return groups
     */
    default Span[] get(Hit hit) {
        return get(hit, false);
    }

    /**
     * Get the captured groups.
     *
     * @param hit hit to get groups for
     * @param omitEmpty if true, instead of a Span with length 0, null will be returned (default: false)
     * @return groups
     */
    Span[] get(Hit hit, boolean omitEmpty);

    /**
     * Get a map of the captured groups.
     *
     * Relatively slow. If you care about performance, prefer {@link #get(Hit)}.
     *
     * @param hit hit to get groups for
     * @return groups
     */
    default Map<String, Span> getMap(Hit hit) {
        return getMap(hit, false);
    }

    /**
     * Get a map of the captured groups.
     *
     * Relatively slow. If you care about performance, prefer {@link #get(Hit)}.
     *
     * @param hit hit to get groups for
     * @param omitEmpty if true, instead of a Span with length 0, null will be returned (default: false)
     * @return groups
     */
    Map<String, Span> getMap(Hit hit, boolean omitEmpty);

    @Override
    String toString();

}
