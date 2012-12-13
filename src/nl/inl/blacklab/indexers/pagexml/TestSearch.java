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
package nl.inl.blacklab.indexers.pagexml;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.HitsWindow;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternPrefix;
import nl.inl.blacklab.search.grouping.HitPropertyHitText;
import nl.inl.util.PropertiesUtil;
import nl.inl.util.XmlUtil;

import org.apache.lucene.search.spans.SpanQuery;

/**
 * Simple test program to demonstrate corpus search functionality.
 */
public class TestSearch {
	public static void main(String[] args) throws IOException {

		// Read property file
		if (args.length == 0) {
			System.err.println("Usage: TestSearch <path_to_prop_file>");
			System.exit(0);
		}
		File propFile = new File(args[0]);
		File baseDir = propFile.getParentFile();
		Properties properties = PropertiesUtil.readFromFile(propFile);

		// Instantiate Searcher object
		File indexDir = PropertiesUtil.getFileProp(properties, "indexDir", "index", baseDir);
		Searcher searcher = new Searcher(indexDir);
		try {
			// Keep track of time
			long time = System.currentTimeMillis();

			TextPattern tp = new TextPatternPrefix("pad");
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
			SpanQuery query = searcher.createSpanQuery("contents", tp);
			Hits hits = searcher.find(query, "contents");

			// Find context (for sorting/grouping)
			hits.findContext(); // NOTE: would be nicer if BlackLab detects if/when this is needed

			// Sort hits on right context
			hits.sort(new HitPropertyHitText(searcher, "contents"));

			// Limit results to the first n
			HitsWindow window = new HitsWindow(hits, 0, 100);

			// Find content concordances (for display)
			window.findConcordances();

			// Print each hit
			for (Hit hit : window) {
				Concordance conc = window.getConcordance(hit);
				String left = XmlUtil.xmlToPlainText(conc.left);
				String hitText = XmlUtil.xmlToPlainText(conc.hit);
				String right = XmlUtil.xmlToPlainText(conc.right);
				System.out.printf("[%05d:%06d] %45s[%s]%s\n", hit.doc, hit.start, left, hitText,
						right);
			}
			System.out.println(window.size() + " concordances of a total of " + window.totalHits());

			System.out.println((System.currentTimeMillis() - time) + "ms elapsed");

			// Fetch and show whole XML doc
			//System.out.println(searcher.getContent(searcher.document(doc), "contents"));

		} finally {
			searcher.close();
		}
	}
}
