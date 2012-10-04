/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Exception utilities
 */
public class ExUtil {

	/**
	 * If the supplied exception is not already an instance of RuntimeException, wrap it in a
	 * RuntimeException object.
	 *
	 * @param e
	 *            the exception to wrap
	 * @return the (possibly) wrapped exception
	 */
	public static RuntimeException wrapRuntimeException(Throwable e) {
		if (e instanceof RuntimeException)
			return (RuntimeException) e;
		return new RuntimeException(e);
	}

	/**
	 * Returns the stack trace of an exception in String form
	 *
	 * @param exception
	 *            the exception
	 * @return the stack trace
	 */
	public static String getStackTraceAsString(Throwable exception) {
		final StringWriter sw = new StringWriter();
		exception.printStackTrace(new PrintWriter(sw));
		String stackTrace_ = sw.toString();
		return stackTrace_;
	}

}
