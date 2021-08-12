package nl.inl.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ThreadAborter {
    private static final Logger logger = LogManager.getLogger(ThreadAborter.class);

    public static ThreadAborter create() {
        return new ThreadAborter();
    }

    private static long now() {
        return System.currentTimeMillis();
    }

    /** The thread that we might want to abort. */
    private Thread thread;

    private long startTimeMs;

    private ThreadAborter() {
        thread = Thread.currentThread();
        startTimeMs = now();
    }

    /**
     * If the thread we're controlling is supposed to be aborted, throw an exception.
     *
     * @throws InterruptedException if thread was interrupted from elsewhere (e.g. load manager)
     */
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

    /**
    * How long has this job been running currently?
    *
    * This does not include any previous running phases.
    *
    * @return number of ms since the job was set to running, or 0 if not running
    */
    public long runningForMs() {
       return now() - startTimeMs;
    }

}
