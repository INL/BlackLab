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

	/** Root of the BLSpans tree for this query. */
	private BLSpans rootSpans;

	/** Captured group names for our query, in index order */
	List<String> groupNames = new ArrayList<String>();

	/** Mapping from captured group names to their index */
	Map<String, Integer> groupNameToIndex = new HashMap<String, Integer>();

	public HitQueryContext(BLSpans rootSpans) {
		this.rootSpans = rootSpans;
	}

	/**
	 * Register a captured group, assigning it a unique index number.
	 *
	 * @param name the group's name
	 * @return the group's assigned index
	 */
	public int registerCapturedGroup(String name) {
		groupNames.add(name);
		int index = groupNames.size() - 1;
		groupNameToIndex.put(name, index);
		return index;
	}

	/**
	 * Get the number of captured groups
	 * @return number of captured groups
	 */
	public int numberOfCapturedGroups() {
		return groupNames.size();
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
		Span[] capturedGroups = new Span[groupNames.size()];
		rootSpans.getCapturedGroups(capturedGroups);
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
