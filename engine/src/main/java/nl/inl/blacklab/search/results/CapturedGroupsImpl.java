package nl.inl.blacklab.search.results;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.lang3.StringUtils;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.lucene.RelationInfo;

/** Captured group information for a list of hits. */
public class CapturedGroupsImpl implements CapturedGroups {

    /** The captured groups per hit. */
    private final Map<Hit, RelationInfo[]> capturedGroups;

    /** Capture group names. */
    private final List<String> capturedGroupNames;

    public CapturedGroupsImpl(List<String> capturedGroupNames) {
        this.capturedGroupNames = capturedGroupNames;
        capturedGroups = new HashMap<>();
    }

    /**
     * Add groups for a hit
     *
     * @param hit the hit
     * @param groups groups for thishit
     */
    public void put(Hit hit, RelationInfo[] groups) {
        capturedGroups.put(hit, groups);
    }

    /**
     * Get the group names
     *
     * @return group names
     */
    @Override
    public List<String> names() {
        return capturedGroupNames;
    }

    /**
     * Get the captured groups.
     *
     * @param hit hit to get groups for
     * @return groups
     */
    @Override
    public RelationInfo[] get(Hit hit, boolean omitEmpty) {
        if (capturedGroups == null)
            return null;
        RelationInfo[] groups = capturedGroups.get(hit);
        if (omitEmpty) {
            // We don't want any Spans where start and end are equal. Replace them with null instead.
            RelationInfo[] withoutEmpty = null;
            for (int i = 0; i < groups.length; i++) {
                if (groups[i].isFullSpanEmpty()) {
                    if (withoutEmpty == null) {
                        withoutEmpty = new RelationInfo[groups.length];
                        System.arraycopy(groups, 0, withoutEmpty, 0, groups.length);
                    }
                    withoutEmpty[i] = null;
                }
            }
            if (withoutEmpty != null)
                return withoutEmpty;
        }
        // We don't mind empty captures, or there are none.
        return groups;
    }

    /**
     * Get a map of the captured groups.
     *
     * Relatively slow. If you care about performance, prefer {@link #get(Hit)}.
     *
     * Please note that if a group was not matched, its key will be in the map,
     * but the associated value will be null.
     *
     * @param hit hit to get groups for
     * @return groups
     */
    @Override
    public Map<String, RelationInfo> getMap(Hit hit, boolean omitEmpty) {
        if (capturedGroups == null)
            return null;
        List<String> names = names();
        RelationInfo[] groups = capturedGroups.get(hit);
        if (groups == null)
            return null;
        Map<String, RelationInfo> result = new TreeMap<>(); // TreeMap to maintain group ordering
        for (int i = 0; i < names.size(); i++) {
            if (!omitEmpty || (groups[i] != null && !groups[i].isFullSpanEmpty())) {
                result.put(names.get(i), groups[i]);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    @Override
    public String toString() {
        String grp = StringUtils.abbreviate(capturedGroups.toString(), 80);
        return "CapturedGroupsImpl(names=" + capturedGroupNames + ", groups=" + grp + ")";
    }

}
