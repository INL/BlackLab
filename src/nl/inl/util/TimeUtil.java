/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.util;

public class TimeUtil {

	public static double secondsSince(long startTime) {
		return (System.currentTimeMillis() - startTime) / 1000.0;
	}

	public static long millisToSeconds(long timeMillis) {
		return Math.round(timeMillis / 1e3);
	}

}
