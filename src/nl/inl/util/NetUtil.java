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

import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;

/** Utilities for networking */
public class NetUtil {
	/** The default encoding to use in networking (as recommended by W3C) */
	private static String defaultEncoding = "utf-8";

	/**
	 * Get the default encoding to use in networking
	 *
	 * @return the default encoding
	 */
	public static String getDefaultEncoding() {
		return defaultEncoding;
	}

	/**
	 * Set the default encoding to use in networking
	 *
	 * @param defaultEncoding
	 *            the default encoding
	 */
	public static void setDefaultEncoding(String defaultEncoding) {
		NetUtil.defaultEncoding = defaultEncoding;
	}

	/**
	 * Get this host's name.
	 *
	 * @return the host name
	 * @throws UnknownHostException
	 */
	public static String getHostName() throws UnknownHostException {
		String hostname;
		hostname = InetAddress.getLocalHost().getHostName();
		return hostname;
	}

	/**
	 * URLencode a value
	 *
	 * @param value
	 *            the value to encode
	 * @return the URLencoded value
	 */
	public static String urlEncode(String value) {
		try {
			return URLEncoder.encode(value, defaultEncoding);
		} catch (UnsupportedEncodingException e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * URLencode a string for use in part of the URL (not a parameter value).
	 *
	 * The difference between encoding a parameter value and an URL part is that
	 * space is encoded as "%20", not "+".
	 *
	 * @param string
	 *            the string to encode
	 * @return the URLencoded value
	 */
	public static String urlPartEncode(String string) {
		try {
			return URLEncoder.encode(string, defaultEncoding).replaceAll("\\+", "%20");
		} catch (UnsupportedEncodingException e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * URLdecode a value
	 *
	 * @param value
	 *            the value to decode
	 * @return the URLdecoded value
	 */
	public static String urlDecode(String value) {
		try {
			return URLDecoder.decode(value, defaultEncoding);
		} catch (UnsupportedEncodingException e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

}
