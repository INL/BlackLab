/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
/**
 *
 */
package nl.inl.util;

/**
 * Simple class for measuring elapsed time.
 */
public class Timer {
	private long start;

	public Timer() {
		reset();
	}

	public long elapsed() {
		return System.currentTimeMillis() - start;
	}

	public void reset() {
		start = System.currentTimeMillis();
	}

	/**
	 * Describe the elapsed time in a human-readable way.
	 *
	 * TODO: why only elapsed time? Generalise to describe any supplied interval.
	 *
	 * @return human-readable string for the elapsed time.
	 */
	public String elapsedDescription() {
		long sec = elapsed() / 1000;
		long min = sec / 60;
		sec %= 60;
		return min + " minutes, " + sec + " seconds";
	}
}
