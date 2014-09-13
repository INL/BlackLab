package nl.inl.blacklab.search.lucene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.Span;

/**
 * Provides per-hit query-wide context, such as capture groups.
 *
 * This object is passed to the whole BLSpans tree before iterating
 * over the hits. Capture groups will register themselves here and
 * BLSpans objects that need access to capture groups will store a
 * reference to this context and use it later.
 */
public class HitQueryContext {

	/** Capture group names for our query, in index order */
	List<String> groupNames = new ArrayList<String>();

	/** Capture group BLSpans objects for our query, in index order */
	List<BLSpans> groups = new ArrayList<BLSpans>();

	/** Mapping from capture group names to their index */
	Map<String, Integer> groupNameToIndex = new HashMap<String, Integer>();

	/**
	 * Register a capture group.
	 *
	 * @param name the group's name
	 * @param spans the group's BLSpans object.
	 */
	public void registerCaptureGroup(String name, BLSpans spans) {
		groupNameToIndex.put(name, groups.size());
		groupNames.add(name);
		groups.add(spans);
	}

	/**
	 * Get the number of capture groups
	 * @return number of capture groups
	 */
	public int numberOfCaptureGroups() {
		return groups.size();
	}

	/**
	 * Get the BLSpans object for a capture group, by index
	 * @param index the group's index
	 * @return the BLSpans object
	 */
	public BLSpans captureGroup(int index) {
		return groups.get(index);
	}

	/**
	 * Get the BLSpans object for a capture group, by name
	 * @param name group name
	 * @return the BLSpans object
	 */
	public BLSpans captureGroup(String name) {
		return captureGroup(captureGroupIndex(name));
	}

	/**
	 * Get the index of a named capture group.
	 *
	 * @param name group name
	 * @return the group's index
	 */
	public int captureGroupIndex(String name) {
		return groupNameToIndex.get(name);
	}

	/**
	 * Retrieve all the capture group information.
	 *
	 * Used by Hits.
	 *
	 * @return the capture group information
	 */
	public Span[] getCaptureGroups() {
		Span[] captureGroups = new Span[groups.size()];
		int i = 0;
		for (BLSpans groupSpans: groups) {
			captureGroups[i] = groupSpans.getSpan();
			i++;
		}
		return captureGroups;
	}

	/**
	 * Get the names of the capture groups, in index order.
	 *
	 * @return the list of names
	 */
	public List<String> getCaptureGroupNames() {
		return Collections.unmodifiableList(groupNames);
	}

}
