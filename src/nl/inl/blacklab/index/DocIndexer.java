/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.index;

/**
 * Indexes a file.
 */
public interface DocIndexer {
	/**
	 * Thrown when the maximum number of documents has been reached
	 */
	public static class MaxDocsReachedException extends RuntimeException {
		//
	}

	/**
	 * Index documents contained in a file.
	 *
	 * @throws Exception
	 */
	public void index() throws Exception;

}
