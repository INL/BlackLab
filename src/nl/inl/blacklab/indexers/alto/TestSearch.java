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
package nl.inl.blacklab.indexers.alto;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.HitsWindow;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternTerm;
import nl.inl.blacklab.search.grouping.HitProperty;
import nl.inl.blacklab.search.grouping.HitPropertyWordRight;
import nl.inl.util.PropertiesUtil;

/**
 * Simple test program to demonstrate corpus search functionality.
 */
public class TestSearch {
	public static void main(String[] args) throws IOException {
		long time = System.currentTimeMillis();
		System.out.println("Start");

		if (args.length == 0) {
			System.err.println("Usage: TestSearch <path_to_prop_file>");
			System.exit(0);
		}
		File propFile = new File(args[0]);
		File baseDir = propFile.getParentFile();
		Properties properties = PropertiesUtil.readFromFile(propFile);
		File indexDir = PropertiesUtil.getFileProp(properties, "indexDir", "index", baseDir);

		// ------------------------------------------------------------
		// First, some setup
		// ------------------------------------------------------------

		// Create the BlackLab searcher object
		Searcher searcher = new Searcher(indexDir);
		try {
			// ------------------------------------------------------------
			// Now, perform some actual tests
			// ------------------------------------------------------------

			// Keep track of time
			System.out.println((System.currentTimeMillis() - time) / 1000 + "s elapsed");
			time = System.currentTimeMillis();

			// Build pattern to search for
			TextPattern tp = new TextPatternTerm("regering");
			// TextPattern tp = new TextPatternTerm("de");
			// TextPattern tp = new TextPatternSequence(new TextPatternTerm("de"), new
			// TextPatternTerm("de"));
			// TextPattern tp = new TextPatternRepetition(new TextPatternWildcard("de*"), 2, 3);
			// TextPattern tp = new TextPatternSequence(new TextPatternTerm("de"), new
			// TextPatternAnyToken(1, 2), new TextPatternTerm("de"));
			// TextPattern tp = new TextPatternSequence(new TextPatternAnyToken(1, 2), new
			// TextPatternTerm("heer"));
			// TextPattern tp = new TextPatternSequence(new TextPatternTerm("heer"), new
			// TextPatternAnyToken(1, 2));

			// Execute search
			Hits hits = searcher.find("contents", tp);

			// Find term vector concordances (for sorting/grouping)
			hits.findContext();

			// Sort results
			HitProperty first = new HitPropertyWordRight();
			hits.sort(first);

			// Limit results to the first n
			HitsWindow window = new HitsWindow(hits, 0, 1000);

			// Find content concordances (for display)
			window.findConcordances();

			// Print each hit
			int doc = 0;
			for (Hit hit : window) {
				doc = hit.doc;
				String left = AltoUtils.getFromContentAttributes(hit.conc[0]) + " ";
				String hitText = AltoUtils.getFromContentAttributes(hit.conc[1]);
				String right = " " + AltoUtils.getFromContentAttributes(hit.conc[2]);
				System.out.printf("[%05d:%06d] %45s[%s]%s\n", hit.doc, hit.start, left, hitText,
						right);
			}
			System.out.println(window.size() + " concordances of a total of " + window.totalHits());

			System.out.println((System.currentTimeMillis() - time) + "ms elapsed");

			// Fetch and show whole XML doc
			System.out.println(searcher.getContent(searcher.document(doc), "contents"));

			// ------------------------------------------------------------
			// Done; clean up
			// ------------------------------------------------------------

		} finally {
			searcher.close();
		}
	}

}
