package nl.inl.blacklab.search.results;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import nl.inl.blacklab.search.Span;

/** Captured group information for a list of hits. */
public class CapturedGroupsImpl implements CapturedGroups {
    
    /** The captured groups per hit. */
    private Map<Hit, Span[]> capturedGroups;
    
    /** Capture group names. */
    private List<String> capturedGroupNames;

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
    public void put(Hit hit, Span[] groups) {
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
    public Span[] get(Hit hit) {
        if (capturedGroups == null)
            return null;
        return capturedGroups.get(hit);
    }

    /**
     * Get a map of the captured groups.
     * 
     * Relatively slow. If you care about performance, prefer {@link #get(Hit)}.
     * 
     * @param hit hit to get groups for
     * @return groups
     */
    @Override
    public Map<String, Span> getMap(Hit hit) {
        if (capturedGroups == null)
            return null;
        Map<String, Span> result = new TreeMap<>(); // TreeMap to maintain group ordering
        List<String> names = names();
        Span[] groups = capturedGroups.get(hit);
        if (groups == null)
            return null;
        for (int i = 0; i < names.size(); i++) {
            result.put(names.get(i), groups[i]);
        }
        return result;
    }

}