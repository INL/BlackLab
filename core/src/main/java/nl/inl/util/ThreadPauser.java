package nl.inl.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import nl.inl.blacklab.search.Pausible;

/**
 * Allows us to pause the thread.
 *
 * The thread must cooperate by calling waitIfPaused() regularly. We don't use Java's
 * own thread priority system because it's not particularly portable / practical
 * to use (differences in priority between OS'es, needs root on Linux, etc.)
 */
public class ThreadPauser implements Pausible {

    /** @param enabled Do we want to enable this functionality? (default: false) */
    public static void setEnabled(boolean enabled) {
        ThreadPauser.enabled = enabled;
    }

    private static final Logger logger = LogManager.getLogger(ThreadPauser.class);

    /** Do we want to enable this functionality? (default: false) */
    private static boolean enabled = false;

    /** The thread we're running in. */
    private Thread currentThread;

    /** What's the intended priority level? */
    private boolean paused = false;

    public ThreadPauser() {
        currentThread = Thread.currentThread();
    }

    /**
     * Wait a short time if this thread is supposed to be paused.
     *
     * @throws InterruptedException if operation was taking too long, or the thread
     *             was interrupted from elsewhere
     */
    public void waitIfPaused() throws InterruptedException {
        while (paused) {
            if (currentThread != null) {
                if (currentThread.isInterrupted()) {
                    logger.debug("Thread was interrupted, throw exception");
                    throw new InterruptedException("Operation aborted");
                }
                if (!currentThread.isAlive()) {
                    currentThread = null; // don't need this anymore
                }
            }

            if (!enabled)
                return;

            Thread.sleep(100);
        }
    }

    @Override
    public void pause(boolean paused) {
        this.paused = paused;
    }

    @Override
    public boolean isPaused() {
        return paused;
    }
}
