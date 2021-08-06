package nl.inl.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Allows us to abort the thread.
 *
 * The thread must cooperate by calling checkAbort() regularly. We don't use Java's
 * own thread priority system because it's not particularly portable / practical
 * to use (differences in priority between OS'es, needs root on Linux, etc.)
 */
public class ThreadAborterImpl implements ThreadAborter {

    private static long now() {
        return System.currentTimeMillis();
    }

    private static final Logger logger = LogManager.getLogger(ThreadAborterImpl.class);

    /** The thread that we might want to abort. */
    private Thread thread;

    private long startTimeMs;

    ThreadAborterImpl() {
        thread = Thread.currentThread();
        startTimeMs = now();
    }

    @Override
    public void checkAbort() throws InterruptedException {
        if (thread != null) {
            if (thread.isInterrupted()) {
                logger.debug("Thread was interrupted, throw exception");
                throw new InterruptedException("Operation aborted");
            }
            if (!thread.isAlive()) {
                thread = null; // don't need this anymore
            }
        }
    }

    @Override
    public long runningForMs() {
       return now() - startTimeMs;
    }

}
