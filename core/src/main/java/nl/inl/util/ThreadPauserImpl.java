package nl.inl.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Allows us to pause the thread.
 *
 * The thread must cooperate by calling waitIfPaused() regularly. We don't use Java's
 * own thread priority system because it's not particularly portable / practical
 * to use (differences in priority between OS'es, needs root on Linux, etc.)
 */
public class ThreadPauserImpl implements ThreadPauser {

    /** @param enabled Do we want to enable this functionality? (default: false) */
    public static void setEnabled(boolean enabled) {
        ThreadPauserImpl.enabled = enabled;
    }

    private static long now() {
        return System.currentTimeMillis();
    }
    
    private static final Logger logger = LogManager.getLogger(ThreadPauserImpl.class);

    /** Do we want to enable this functionality? (default: false) */
    private static boolean enabled = false;

    /** The thread that we're pausing or unpausing. */
    private Thread thread;

    /** What's the intended priority level? */
    private boolean paused = false;

    private long setToPausedTime = 0;
    
    private long setToRunningTime;

    private long pausedTime = 0;
    
    ThreadPauserImpl() {
        thread = Thread.currentThread();
        setToRunningTime = now();
    }

    /* (non-Javadoc)
     * @see nl.inl.util.ThreadPauserInterface#waitIfPaused()
     */
    @Override
    public void waitIfPaused() throws InterruptedException {
        while (paused) {
            if (thread != null) {
                if (thread.isInterrupted()) {
                    logger.debug("Thread was interrupted, throw exception");
                    throw new InterruptedException("Operation aborted");
                }
                if (!thread.isAlive()) {
                    thread = null; // don't need this anymore
                }
            }

            if (!enabled)
                return;

            Thread.sleep(100);
        }
    }

    /* (non-Javadoc)
     * @see nl.inl.util.ThreadPauserInterface#pause(boolean)
     */
    @Override
    public void pause(boolean paused) {
        this.paused = paused;
        if (paused)
            setToPausedTime = now();
        else {
            // Unpause. Keep track of how long we've been paused total.
            pausedTime += now () - setToPausedTime;
            setToRunningTime = now();
        }
    }

    /* (non-Javadoc)
     * @see nl.inl.util.ThreadPauserInterface#isPaused()
     */
    @Override
    public boolean isPaused() {
        return paused;
    }        /* (non-Javadoc)
     * @see nl.inl.util.ThreadPauserInterface#currentPauseLength()
     */
   @Override
public long currentPauseLength() {
       if (!isPaused())
           return 0;
       return now() - setToPausedTime;
   }

   /* (non-Javadoc)
 * @see nl.inl.util.ThreadPauserInterface#currentRunPhaseLength()
 */
   @Override
public long currentRunPhaseLength() {
       if (isPaused())
           return 0;
       return now() - setToRunningTime;
   }

   /* (non-Javadoc)
 * @see nl.inl.util.ThreadPauserInterface#pausedTotal()
 */
   @Override
public long pausedTotal() {
       if (!isPaused())
           return pausedTime;
       return pausedTime + now() - setToPausedTime;
   }

}
