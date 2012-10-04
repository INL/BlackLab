/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
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
