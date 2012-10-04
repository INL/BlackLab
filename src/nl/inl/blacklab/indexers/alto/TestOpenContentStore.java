/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.indexers.alto;

import java.io.File;

import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.externalstorage.ContentStoreDirZip;

/**
 * The indexer class and main program for the ANW corpus.
 */
public class TestOpenContentStore {
	public static void main(String[] args) throws Exception {
		File dir = new File("D:\\dev\\blacklab\\pagexml\\index\\xml");
		ContentStore contentStore = new ContentStoreDirZip(dir);

		Thread.sleep(1000 * 60); // Allow time to analyze memory

		contentStore.close();
	}
}
