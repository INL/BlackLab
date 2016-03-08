/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.util;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Exception utilities
 */
public class ExUtil {

	private ExUtil() {
	}

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
	 * @param e the exception
	 * @return the stack trace
	 */
	public static String getStackTraceAsString(Throwable e) {
		return getStackTraceAsString(e, false);
	}

	/**
	 * Returns the stack trace of an exception in String form
	 *
	 * @param e the exception
	 * @param singleLine if true, put stack trace on single line with escaped newlines
	 * @return the stack trace
	 */
	public static String getStackTraceAsString(Throwable e, boolean singleLine) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		String trace = sw.toString();
		if (singleLine)
			trace = trace.replaceAll("[\r\n]+", "\\\\n");
		return trace;
	}

}
