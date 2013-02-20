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
import java.io.IOException;
import java.util.Properties;

import nl.inl.blacklab.search.Concordance;
import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.HitsWindow;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternTerm;
import nl.inl.blacklab.search.lucene.TextPatternTranslatorSpanQuery;
import nl.inl.blacklab.search.sequences.TextPatternSequence;
import nl.inl.util.PropertiesUtil;
import nl.inl.util.XmlUtil;

import org.apache.lucene.search.Filter;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.spans.SpanQuery;

/**
 * Simple test program to demonstrate corpus search functionality.
 */
public class TestCorpusSearch {
	public static void main(String[] args) throws IOException {
		// Read property file
		Properties properties = PropertiesUtil.getFromResource("anwcorpus.properties");

		// Where to create the index and UTF-16 content
		File indexDir = PropertiesUtil.getFileProp(properties, "indexDir", "index", null);

		// Create the BlackLab searcher object
		Searcher searcher = new Searcher(indexDir);
		try {
			// Perform the actual tests
			performTestSearches(searcher);
		} finally {
			searcher.close();
		}
	}

	private static void performTestSearches(Searcher searcher) {
		TextPattern pattern;

		System.out.println("---");
		pattern = new TextPatternTerm("he");
		// pattern = new TextPatternRegex("beschr.*ing");
		// pattern = new TextPatternAnd(new TextPatternTerm("de"), new TextPatternTerm("het"));
		patternSearch(searcher, "contents", pattern, 20);

		patternSearchNonSpan(searcher, "contents", pattern, 20);

		// Search simple field (not linguistically enriched; no word, hw, pos properties)
		System.out.println("---");
		patternSearch(searcher, "bronentitel", new TextPatternTerm("de"), 20);

		// Search for a sequence of words
		System.out.println("---");
		pattern = new TextPatternSequence(new TextPatternTerm("en"), new TextPatternTerm("waar"));
		patternSearch(searcher, "contents", pattern, 20);
	}

	private static void patternSearchNonSpan(Searcher searcher, String fieldName,
			TextPattern pattern, int n) {
		TextPatternTranslatorSpanQuery translator = new TextPatternTranslatorSpanQuery();
		SpanQuery query = pattern.translate(translator, searcher.getDefaultTranslationContext(fieldName));
		SpanQuery spanQuery = query;
		TopDocs d = searcher.findTopDocs(spanQuery, 30);
		for (ScoreDoc sd : d.scoreDocs) {
			System.out.println("Doc = " + sd.doc + "; score = " + sd.score);
		}
	}

	private static void patternSearch(Searcher searcher, String fieldName, TextPattern pattern,
			int n) {
		patternSearch(searcher, fieldName, pattern, n, null);
	}

	private static void patternSearch(Searcher searcher, String fieldName, TextPattern pattern,
			int n, Filter filter) {
		Hits hits = searcher.find(pattern, fieldName, filter);

		// Limit results to the first n
		HitsWindow window = new HitsWindow(hits, 0, n);

		displayConcordances(searcher, window);
	}

	private static void displayConcordances(Searcher searcher, HitsWindow window) {
		for (Hit hit : window) {
			Concordance conc = window.getConcordance(hit);
			String left = XmlUtil.xmlToPlainText(conc.left);
			String hitText = XmlUtil.xmlToPlainText(conc.hit);
			String right = XmlUtil.xmlToPlainText(conc.right);
			System.out.printf("[%05d:%06d] %45s[%s]%s\n", hit.doc, hit.start, left, hitText, right);
		}
	}
}
