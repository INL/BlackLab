package nl.inl.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Simple pausable timer that we can also manually add time to.
 *
 * Used to keep track of how long a search task (originally) took, including subtasks.
 */
public class TaskTimer {
    private static final Logger logger = LogManager.getLogger(TaskTimer.class);

    /**
     * When was our processing timer started?
     */
    private long startTime = -1;

    /**
     * Is our timer currently running?
     */
    private boolean timerRunning = false;

    /**
     * Total processing time for this task (ms), including that of subtasks.
     */
    private long processingTime = 0;

    /**
     * Get the total processing time for this task (ms).
     *
     * If the timer is running, returns the running time.
     *
     * This includes processing time for other tasks it used (e.g. a "sorted hits" task calculates
     * its processing time by adding the time it took to retrieve all the hits and the time it took
     * to sort them, even though the task itself only does the actual sorting).
     *
     * Processing time is intended to be independent from the cache: it keeps track only of the actual
     * time processing (originally) took. So even if a request is almost instant, processing time can
     * be much higher if the original search took that long.
     */
    public long time() {
        return timerRunning ? processingTime + (System.currentTimeMillis() - startTime) : processingTime;
    }

    /**
     * (Re)start the task's processing timer, adding to its total.
     */
    public void start() {
        if (timerRunning)
            logger.debug("@@@@@ Timer.start: timer already running!");
        startTime = System.currentTimeMillis();
        timerRunning = true;
    }

    /**
     * Stop the task's processing timer, (temporarily) not keeping track of time elapsed.
     */
    public void stop() {
        if (timerRunning) {
            processingTime += System.currentTimeMillis() - startTime;
            timerRunning = false;
        } else {
            logger.debug("@@@@@ Timer.stop: timer wasn't running!");
        }
    }

    /**
     * Add the processing time for the subtask to this tasks's processing time.
     */
    public void add(long ms) {
        if (timerRunning)
            logger.debug("@@@@@ Timer.add: timer should not be running!");
        processingTime += ms;
    }
}
