/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.indexers.pagexml;

import java.io.File;
import java.util.Properties;

import nl.inl.blacklab.index.Indexer;
import nl.inl.util.PropertiesUtil;

/**
 * The indexer class and main program for the ANW corpus.
 */
public class IndexPageXml {
	public static void main(String[] args) throws Exception {
		System.out.println("IndexPageXml\n");
		if (args.length != 1) {
			System.out
					.println("Usage: java nl.inl.blacklab.indexers.pagexml.IndexPageXml <datasetname>\n"
							+ "(Will look for a <datasetname>.properties on the classpath; "
							+ "see installation & indexing guide for more information)");
			return;
		}
		String dataSetName = args[0];

		// Read property file
		Properties properties = PropertiesUtil.getFromResource(dataSetName + ".properties");

		// The indexer tool
		File indexDir = PropertiesUtil.getFileProp(properties, "indexDir", null);
		Indexer indexer = new Indexer(indexDir, true, DocIndexerPageXml.class);
		try {
			// How many documents to process (0 = all of them)
			int maxDocs = PropertiesUtil.getIntProp(properties, "maxDocs", 0);
			if (maxDocs > 0)
				indexer.setMaxDocs(maxDocs);

			// Where the source files are
			File inputDir = PropertiesUtil.getFileProp(properties, "inputDir", null);
			indexer.indexDir(inputDir, true);
		} catch (Exception e) {
			System.err.println("An error occurred, aborting indexing. Error details follow.");
			e.printStackTrace();
		} finally {
			// Close the index.
			indexer.close();
		}
	}
}
