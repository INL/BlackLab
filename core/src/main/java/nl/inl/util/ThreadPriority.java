package nl.inl.util;

import org.apache.log4j.Logger;

import nl.inl.blacklab.search.Prioritizable;

/** Allows us to lower thread priority and/or pause thread.
 *
 * The thread must cooperate by calling behave() regularly.
 * We don't use Java's own thread priority system because
 * it's not particularly portable / practical to use (differences
 * in priority between OS'es, needs root on Linux, etc.)
 */
public class ThreadPriority implements Prioritizable {

	/**
	 * The different priorities a thread can have in our system.
	 */
	public enum Level {
		PAUSED,
		RUNNING_LOW_PRIO,
		RUNNING
	}

	protected static final Logger logger = Logger.getLogger(ThreadPriority.class);

	/** Do we want to enable this functionality? (default: false) */
	private static boolean enabled = false;

	/** @param enabled Do we want to enable this functionality? (default: false) */
	public static void setEnabled(boolean enabled) {
		ThreadPriority.enabled = enabled;
	}

	/** The thread we're running in. */
	Thread currentThread;

	/** What's the intended priority level? */
	private Level level = Level.RUNNING;

	/**
	 * Create a ThreadEtiquette object.
	 */
	public ThreadPriority() {
		reset();
	}

	@Override
	public void setPriorityLevel(Level level) {
		this.level = level;
	}

	@Override
	public Level getPriorityLevel() {
		return level;
	}

	public void reset() {
		currentThread = Thread.currentThread();
	}

	/**
	 * Make sure our thread is behaving like a respectable citizen.
	 *
	 * That means: if it's taking long, it should sleep from time to time;
	 * if it's taking too long, it should be interrupted.
	 *
	 * @throws InterruptedException if operation was taking too long,
	 *   or the thread was interrupted from elsewhere
	 */
	public void behave() throws InterruptedException {

		if (!enabled)
			return;

		if (currentThread.isInterrupted()) {
			logger.debug("Thread was interrupted, throw exception");
			throw new InterruptedException("Operation aborted");
		}

		while (level != Level.RUNNING) {
			Thread.sleep(100);
		}
	}
}
