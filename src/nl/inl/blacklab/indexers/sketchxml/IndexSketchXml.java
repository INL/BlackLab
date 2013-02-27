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
package nl.inl.blacklab.indexers.sketchxml;

import java.io.File;
import java.util.Properties;

import nl.inl.blacklab.index.Indexer;
import nl.inl.util.LogUtil;
import nl.inl.util.PropertiesUtil;

/**
 * The indexer class and main program for the Sketch XML format.
 */
public class IndexSketchXml {
	public static void main(String[] args) throws Exception {
		System.out.println("IndexSketchXml\n");
		if (args.length != 1) {
			System.out
					.println("Usage: java nl.inl.blacklab.indexers.sketchxml.IndexSketchXml <propfile>\n"
							+ "(see docs for more information)");
			return;
		}
		File propFile = new File(args[0]);
		File baseDir = propFile.getParentFile();
		//String dataSetName = args[0];

		LogUtil.initLog4jBasic();

		// Read property file
		Properties properties = PropertiesUtil.readFromFile(propFile);

		// Where to create the index and UTF-16 content
		File indexDir = PropertiesUtil.getFileProp(properties, "indexDir", "index", baseDir);
		if (!indexDir.isDirectory())
			indexDir.mkdir();

		// The indexer tool
		Indexer indexer = new Indexer(indexDir, true, DocIndexerXmlSketch.class);
		try {
			// How many documents to process (0 = all of them)
			int maxDocs = PropertiesUtil.getIntProp(properties, "maxDocs", 0);
			if (maxDocs > 0)
				indexer.setMaxDocs(maxDocs);

			// Where the source files are
			File inputDir = PropertiesUtil.getFileProp(properties, "inputDir", "input", baseDir);

			// Index a directory
			indexer.indexDir(inputDir, false);
		} catch (Exception e) {
			System.err.println("An error occurred, aborting indexing. Error details follow.");
			e.printStackTrace();
		} finally {
			// Finalize and close the index.
			indexer.close();
		}
	}
}
