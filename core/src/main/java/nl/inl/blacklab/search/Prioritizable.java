package nl.inl.blacklab.search;

import nl.inl.util.ThreadPriority;

public interface Prioritizable {

	/**
	 * Set the thread priority level for this Hits object.
	 *
	 * Allows us to set a query to low-priority, or to (almost) pause it.
	 *
	 * @param level the desired priority level
	 */
	void setPriorityLevel(ThreadPriority.Level level);

	/**
	 * Get the thread priority level for this Hits object.
	 *
	 * Can be normal, low-priority, or (almost) paused.
	 *
	 * @return the current priority level
	 */
	ThreadPriority.Level getPriorityLevel();

}
