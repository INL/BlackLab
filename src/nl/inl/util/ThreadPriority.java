package nl.inl.util;

import org.apache.log4j.Logger;

/** Allows us to lower thread priority and/or pause thread.
 *
 * The thread must cooperate by calling behave() regularly.
 * We don't use Java's own thread priority system because
 * it's not particularly portable / practical to use (differences
 * in priority between OS'es, needs root on Linux, etc.)
 */
public class ThreadPriority {

	/**
	 * The different priorities a thread can have in our system.
	 */
	public static enum Level {
		PAUSED,
		LOW,
		NORMAL
	}

	protected static final Logger logger = Logger.getLogger(ThreadPriority.class);

	private static final int WAKE_SLEEP_CYCLE = 500;

	private static final double LOW_PRIO_SLEEP_PART = 0.5;

	private static final double PAUSED_SLEEP_PART = 0.99;

	/** Do we want to enable this functionality? (default: false) */
	private static boolean enabled = false;

	/** @param enabled Do we want to enable this functionality? (default: false) */
	public static void setEnabled(boolean enabled) {
		ThreadPriority.enabled = enabled;
	}

	/** The thread we're running in. */
	Thread currentThread;

	/** What's the intended priority level? */
	private Level level;

	/** Last call to Thread.sleep(), if any */
	private long lastSleepTimeMs;

	/**
	 * Create a ThreadEtiquette object.
	 */
	public ThreadPriority() {
		reset();
	}

	public void setPriorityLevel(Level level) {
		this.level = level;
	}

	public Level getPriorityLevel() {
		return level;
	}

	public void reset() {
		currentThread = Thread.currentThread();
		lastSleepTimeMs = System.currentTimeMillis();
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

		// If we're either low-priority or paused, sleep from time to time
		// (the difference is how long we sleep; when paused, we sleep almost
		//  100% of the time)
		if (level != Level.NORMAL) {
			// The longer the query takes, the more it will sleep,
			// to a certain maximum (default 50%) of the time.
			double sleepPart = level == Level.PAUSED ? PAUSED_SLEEP_PART : LOW_PRIO_SLEEP_PART;
			int sleepTimeMs = (int)(WAKE_SLEEP_CYCLE * sleepPart);
			int wakeTimeMs = WAKE_SLEEP_CYCLE - sleepTimeMs;
			long now = System.currentTimeMillis();
			int timeSinceSleepMs = (int)(now - lastSleepTimeMs);
			boolean shouldSleepNow = timeSinceSleepMs > wakeTimeMs;
			if (shouldSleepNow) {
				//logger.debug("Sleep for " + sleepTimeMs);
				// Zzzz...
				try {
					Thread.sleep(sleepTimeMs);
				} catch (InterruptedException e) {
					// Set the interrupted flag so the caller may ignore this
					// exception and the client may manually check the flag to see
					// if the thread was interrupted (because not all clients interrupt
					// threads, and we shouldn't force clients who don't to catch this
					// exception anyway).
					currentThread.interrupt();
					throw e;
				}
				lastSleepTimeMs = now;
			}
		}
	}
}
