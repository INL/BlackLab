package nl.inl.blacklab.search.lucene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.Span;

/**
 * Provides per-hit query-wide context, such as captured groups.
 *
 * This object is passed to the whole BLSpans tree before iterating
 * over the hits. Captured groups will register themselves here and
 * BLSpans objects that need access to captured groups will store a
 * reference to this context and use it later.
 */
public class HitQueryContext {

	/** Captured group names for our query, in index order */
	List<String> groupNames = new ArrayList<String>();

	/** Captured group BLSpans objects for our query, in index order */
	List<BLSpans> groups = new ArrayList<BLSpans>();

	/** Mapping from captured group names to their index */
	Map<String, Integer> groupNameToIndex = new HashMap<String, Integer>();

	/**
	 * Register a captured group.
	 *
	 * @param name the group's name
	 * @param spans the group's BLSpans object.
	 */
	public void registerCapturedGroup(String name, BLSpans spans) {
		groupNameToIndex.put(name, groups.size());
		groupNames.add(name);
		groups.add(spans);
	}

	/**
	 * Get the number of captured groups
	 * @return number of captured groups
	 */
	public int numberOfCapturedGroups() {
		return groups.size();
	}

	/**
	 * Get the BLSpans object for a captured group, by index
	 * @param index the group's index
	 * @return the BLSpans object
	 */
	public BLSpans capturedGroup(int index) {
		return groups.get(index);
	}

	/**
	 * Get the BLSpans object for a captured group, by name
	 * @param name group name
	 * @return the BLSpans object
	 */
	public BLSpans capturedGroup(String name) {
		return capturedGroup(capturedGroupIndex(name));
	}

	/**
	 * Get the index of a named captured group.
	 *
	 * @param name group name
	 * @return the group's index
	 */
	public int capturedGroupIndex(String name) {
		return groupNameToIndex.get(name);
	}

	/**
	 * Retrieve all the captured group information.
	 *
	 * Used by Hits.
	 *
	 * @return the captured group information
	 */
	public Span[] getCapturedGroups() {
		Span[] capturedGroups = new Span[groups.size()];
		int i = 0;
		for (BLSpans groupSpans: groups) {
			capturedGroups[i] = groupSpans.getSpan();
			i++;
		}
		return capturedGroups;
	}

	/**
	 * Get the names of the captured groups, in index order.
	 *
	 * @return the list of names
	 */
	public List<String> getCapturedGroupNames() {
		return Collections.unmodifiableList(groupNames);
	}

}
