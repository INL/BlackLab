package nl.inl.blacklab.searches;

public interface SearchTask<T> {
    /**
     * Peek at the result.
     * @return null if not supported, a peek at the result otherwise
     */
    default T peek() { return null; }

    /** Get the total processing time for this task (ms).
     *
     * This includes processing time for other tasks it used (e.g. a "sorted hits" task calculates
     * its processing time by adding the time it took to retrieve all the hits and the time it took
     * to sort them, even though the task itself only does the actual sorting).
     *
     * Processing time is intended to be independent from the cache: it keeps track only of the actual
     * time processing (originally) took. So even if a request is almost instant, processing time can
     * be much higher if the original search took that long.
     */
    long processingTimeMs();

    /** (Re)start the task's processing timer, adding to its total. */
    void startTimer();

    /** Stop the task's processing timer, (temporarily) not keeping track of time elapsed. */
    void stopTimer();

    /** Add the processing time for the subtask to this tasks's processing time. */
    void addSubtaskTime(SearchTask<?> subtask);
}
