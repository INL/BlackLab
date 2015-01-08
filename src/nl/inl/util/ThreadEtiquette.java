package nl.inl.util;

import org.apache.log4j.Logger;

/** Makes sure threads behave nicely towards others, by sleeping
 *  occasionally and optionally interrupting long operations. */
public class ThreadEtiquette {

	protected static final Logger logger = Logger.getLogger(ThreadEtiquette.class);

	/** Do we want to enable this functionality? (default: false) */
	private static boolean enabled = false;

	/** After how many seconds should we sleep occasionally? (default: 5) */
	private static int startSleepingAfterSec = 5;

	/** How many ms 1 wake/sleep cycle takes (default: 1000) */
	private static int wakeSleepCycleMs = 1000;

	/** Increase of sleep part of the cycle per second (default: 0.004) */
	private static double sleepPartIncreaseSpeed = 0.004;

	/** Maximum sleep part of cycle (default: 0.5 == 50%) */
	private static double maxSleepPart = 0.5;

	/** After how many seconds we should interrupt the thread (default: 240 == 4 mins) */
	private static int interruptAfterSec = 240;

	/** @param enabled Do we want to enable this functionality? (default: false) */
	public static void setEnabled(boolean enabled) {
		ThreadEtiquette.enabled = enabled;
	}

	/** @param startSleepingAfterSec After how many seconds should we sleep occasionally? (default: 5) */
	public static void setStartSleepingAfterSec(int startSleepingAfterSec) {
		ThreadEtiquette.startSleepingAfterSec = startSleepingAfterSec;
	}

	/** @param wakeSleepCycleMs How many ms 1 wake/sleep cycle takes (default: 1000) */
	public static void setWakeSleepCycleMs(int wakeSleepCycleMs) {
		ThreadEtiquette.wakeSleepCycleMs = wakeSleepCycleMs;
	}

	/** @param sleepPartIncreaseSpeed Increase of sleep part of the cycle per second (default: 0.002) */
	public static void setSleepPartIncreaseSpeed(double sleepPartIncreaseSpeed) {
		ThreadEtiquette.sleepPartIncreaseSpeed = sleepPartIncreaseSpeed;
	}

	/** @param maxSleepPart Maximum sleep part of cycle (default: 0.5 == 50%) */
	public static void setMaxSleepPart(double maxSleepPart) {
		ThreadEtiquette.maxSleepPart = maxSleepPart;
	}

	/** @param interruptAfterSec After how many seconds we should interrupt the thread (default: 240 == 4 mins) */
	public static void setInterruptAfterSec(int interruptAfterSec) {
		ThreadEtiquette.interruptAfterSec = interruptAfterSec;
	}

	/** The thread we're running in. */
	Thread currentThread;

	/** Start of current (potentially) long operation, like sorting or grouping.
	 *  Used to determine if we should sleep, and eventually terminate it. */
	private long operationStartMs;

	/** Last call to Thread.sleep(), if any */
	private long lastSleepTimeMs;

	private int reportedSec = 0;

	/**
	 * Create a ThreadEtiquette object.
	 */
	public ThreadEtiquette() {
		reset();
	}

	public void reset() {
		currentThread = Thread.currentThread();
		operationStartMs = System.currentTimeMillis();
		lastSleepTimeMs = operationStartMs;
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

		// If we're taking really long, abort.
		long now = System.currentTimeMillis();
		int runningForSec = (int) ((now - operationStartMs) / 1000);
		boolean takingTooLong = interruptAfterSec > 0 && runningForSec > interruptAfterSec;
		if (takingTooLong) {
			logger.debug("We're taking too long; throw exception");
			throw new InterruptedException("Operation taking too long");
		}
		if (runningForSec > reportedSec) {
			reportedSec = runningForSec;
			//logger.debug("Operation running for " + runningForSec);
		}

		// If we're taking relatively long, sleep occasionally
		// to give other threads a chance to run.
		if (runningForSec > startSleepingAfterSec) {
			// The longer the query takes, the more it will sleep,
			// to a certain maximum (default 50%) of the time.
			double sleepPart = Math.min(runningForSec * sleepPartIncreaseSpeed, maxSleepPart);
			int sleepTimeMs = (int)(sleepPart * wakeSleepCycleMs);
			int wakeTimeMs = wakeSleepCycleMs - sleepTimeMs;
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
