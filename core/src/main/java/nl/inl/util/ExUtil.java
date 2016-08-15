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

}
