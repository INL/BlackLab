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

import java.io.IOException;
import java.util.Properties;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.HitsWindow;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternPrefix;
import nl.inl.blacklab.search.grouping.HitPropertyRightContext;
import nl.inl.util.PropertiesUtil;
import nl.inl.util.XmlUtil;

/**
 * Simple test program to demonstrate corpus search functionality.
 */
public class TestSearch {
	public static void main(String[] args) throws IOException {
		// Read property file
		Properties properties = PropertiesUtil.getFromResource("dutch.properties");

		// Instantiate Searcher object
		Searcher searcher = new Searcher(PropertiesUtil.getFileProp(properties, "indexDir", "index", null));
		try {
			// Keep track of time
			long time = System.currentTimeMillis();

			TextPattern tp = new TextPatternPrefix("der");
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
			hits.findConcordances(true); // NOTE: would be nicer if BlackLab detects if/when this is
											// needed

			// Sort hits on right context
			hits.sort(new HitPropertyRightContext());

			// Limit results to the first n
			HitsWindow window = new HitsWindow(hits, 0, 100);

			// Find content concordances (for display)
			window.findConcordances();

			// Print each hit
			for (Hit hit : window) {
				String left = XmlUtil.xmlToPlainText(hit.conc[0]);
				String hitText = XmlUtil.xmlToPlainText(hit.conc[1]);
				String right = XmlUtil.xmlToPlainText(hit.conc[2]);
				System.out.printf("[%05d:%06d] %45s[%s]%s\n", hit.doc, hit.start, left, hitText,
						right);
			}
			System.out.println(window.size() + " concordances of a total of " + window.totalHits());

			System.out.println((System.currentTimeMillis() - time) + "ms elapsed");
		} finally {
			searcher.close();
		}
	}
}
