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
package nl.inl.blacklab.indexers.anwcorpus;

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

		// Search for a regular expression
		// System.out.println("---");
		// pattern = new TextPatternRegex("", ".*ing");
		// patternSearch(searcher, "contents", pattern, 20);
		// wildcardSearch(searcher, "ing", 20);

		System.out.println("---");
		pattern = new TextPatternTerm("he");
		// pattern = new TextPatternRegex("beschr.*ing");
		// pattern = new TextPatternAnd(new TextPatternTerm("de"), new TextPatternTerm("het"));
		patternSearch(searcher, "contents", pattern, 20);

		patternSearchNonSpan(searcher, "contents", pattern, 20);

		// System.out.println("---");
		// patternSearch(searcher, "contents", new TextPatternTerm("zorg"), 20);
		//
		// System.out.println("---");
		// patternSearch(searcher, "contents", new TextPatternTerm("hw", "zorgen"), 20);

		// Search for specific word form and headword
		// System.out.println("---");
		// pattern = new TextPatternAnd(new TextPatternTerm("zorg"), // word == "zorg" AND
		// new TextPatternTerm("hw", "zorgen") // headword == "zorgen"
		// );
		// patternSearch(searcher, "contents", pattern, 20);

		// Search for specific word form and headword
		// System.out.println("---");
		// pattern = new TextPatternAnd(
		// new TextPatternTerm("weet"), // word == "zorg" AND
		// new TextPatternTerm("hw", "weten") // headword == "zorgen"
		// );
		// patternSearch(searcher, "contents", pattern, 20);

		// Search simple field (not linguistically enriched; no word, hw, pos properties)
		System.out.println("---");
		patternSearch(searcher, "bronentitel", new TextPatternTerm("de"), 20);

		// Search for a sequence of words
		System.out.println("---");
		pattern = new TextPatternSequence(new TextPatternTerm("en"), new TextPatternTerm("waar"));
		patternSearch(searcher, "contents", pattern, 20);

		// findLemmatisations(searcher, "zorg");
		// findLemmatisations(searcher, "weet"); // > 4000 occurrences, slow
	}

	private static void patternSearchNonSpan(Searcher searcher, String fieldName,
			TextPattern pattern, int n) {
		// // Execute search
		// Scorer scorer = searcher.findDocScores(fieldName, pattern);
		// try
		// {
		// int i = 0;
		// while (true)
		// {
		// int id = scorer.nextDoc();
		// if (id == DocIdSetIterator.NO_MORE_DOCS)
		// break;
		// float score = scorer.score();
		// System.out.println("Doc = " + id + "; score = " + score);
		// i++;
		// // if (i == n)
		// // break;
		// }
		// }
		// catch (IOException e)
		// {
		// throw ExUtil.wrapRuntimeException(e);
		// }

		TextPatternTranslatorSpanQuery translator = new TextPatternTranslatorSpanQuery();
		SpanQuery query = pattern.translate(translator, fieldName);
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
		SpanQuery query = searcher.createSpanQuery(fieldName, pattern, filter);
		Hits hits = searcher.find(query, "contents");

		// Limit results to the first n
		HitsWindow window = new HitsWindow(hits, 0, n);

		displayConcordances(searcher, window);
	}

	// @SuppressWarnings("unused")
	// private static void findForms(Searcher searcher, String headword)
	// {
	// String[] forms = searcher.wordFormsForHeadword(headword);
	// System.out.println("Vormen van '" + headword + "' in de index: "
	// + StringUtil.join(Arrays.asList(forms), ";"));
	// }

	// /**
	// * Find all lemmatisations for a word form.
	// * @param searcher
	// * @param wordForm
	// */
	// static void findLemmatisations(Searcher searcher, String wordForm)
	// {
	// TextPattern pattern = new TextPatternTerm(wordForm);
	// String fieldName = "contents";
	// SpanQuery spanQuery = getSpanQuery(fieldName, pattern);
	//
	// // Execute search
	// Spans results = searcher.find(spanQuery);
	//
	// // Wrap results object in concordance maker
	// // (we reconstruct the hit text from the term vector because "hw" is not a stored field)
	// results = new SpansConcordances(searcher, results, ComplexFieldUtil
	// .getComplexFieldName(fieldName, "hw"), 0, true);
	//
	// // What do we want to group on?
	// GroupCriteria groupCriteria = new GroupCriteria(new HitPropertyHitText());
	//
	// // Group the results
	// ResultsGrouper grouped = new ResultsGrouper(results, groupCriteria);
	//
	// // Show the groups we got
	// for (RandomAccessGroup group : grouped.getGroups())
	// {
	// System.out.println(group);
	// }
	// }

	private static void displayConcordances(Searcher searcher, HitsWindow window) {
		window.findConcordances();
		for (Hit hit : window) {
			Concordance conc = window.getConcordance(hit);
			String left = XmlUtil.xmlToPlainText(conc.left);
			String hitText = XmlUtil.xmlToPlainText(conc.hit);
			String right = XmlUtil.xmlToPlainText(conc.right);
			System.out.printf("[%05d:%06d] %45s[%s]%s\n", hit.doc, hit.start, left, hitText, right);
		}
	}
}
