package nl.inl.blacklab.searches;

import nl.inl.util.SearchTimer;

/**
 * Most basic interface for a search being (or having been) executed (aka "cache entry").
 *
 * This is passed to the Search.executeInternal() method so it has access
 * to the active search object. This enables it to update the running count
 * during execution, as well as manage the timer (stopping it before executing
 * a subtask, adding the subtask's processing time, and re-starting it for the
 * rest of the execution)
 *
 * @param <T> type of result this task will yield
 */
public interface ActiveSearch<T> {
    /**
     * Peek at the result.
     * @return null if not supported, a peek at the result otherwise
     */
    default T peek() { return null; }

    /**
     * Timer instance for this task.
     * @return tasks's timer
     */
    SearchTimer timer();
}
