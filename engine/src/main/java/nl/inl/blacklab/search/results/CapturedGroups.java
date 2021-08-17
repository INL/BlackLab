package nl.inl.blacklab.search.results;

import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.Span;

/** Captured group information for a list of hits. */
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
    Span[] get(Hit hit);

    /**
     * Get a map of the captured groups.
     *
     * Relatively slow. If you care about performance, prefer {@link #get(Hit)}.
     *
     * @param hit hit to get groups for
     * @return groups
     */
    Map<String, Span> getMap(Hit hit);

    /**
     * Add groups for a hit
     *
     * @param hit the hit
     * @param groups groups for thishit
     */
    default void put(Hit hit, Span[] groups) {
        throw new UnsupportedOperationException();
    }

    /** Copy all groups from other */
    default void putAll(CapturedGroups other) {
        throw new UnsupportedOperationException();
    }

    /** Copy all groups from other */
    default void putAll(Map<Hit, Span[]> other) {
        throw new UnsupportedOperationException();
    }

    Map<? extends Hit, ? extends Span[]> getAll();

    @Override
    String toString();

}
