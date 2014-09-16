package nl.inl.blacklab.search.lucene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import nl.inl.blacklab.search.Span;

/**
 * Provides per-hit query-wide context, such as captured groups.
 *
 * This object is passed to the whole BLSpans tree before iterating
 * over the hits. Captured groups will register themselves here and receive
 * an index in the captured group array, and BLSpans objects that need access
 * to captured groups will store a reference to this context and use it later.
 */
public class HitQueryContext {

	/** Root of the BLSpans tree for this query. */
	private BLSpans rootSpans;

	/** Captured group names for our query, in index order */
	List<String> groupNames = new ArrayList<String>();

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
		return groupNames.size() - 1; // index in array
	}

	/**
	 * Get the number of captured groups
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
	public void getCapturedGroups(Span[] capturedGroups) {
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

}
