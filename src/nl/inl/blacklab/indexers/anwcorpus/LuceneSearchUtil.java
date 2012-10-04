/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.indexers.anwcorpus;

import java.io.IOException;
import java.util.List;

import nl.inl.util.ConsoleUtil;
import nl.inl.util.ExUtil;
import nl.inl.util.TimeUtil;

import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Fieldable;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.Version;

public class LuceneSearchUtil {
	private static boolean printHits = false;

	private static boolean produceOutput = true;

	public static void setProduceOutput(boolean produceOutput) {
		LuceneSearchUtil.produceOutput = produceOutput;
	}

	public static void setPrintHits(boolean printHits) {
		LuceneSearchUtil.printHits = printHits;
	}

	static void reportResults(IndexSearcher searcher, Query query, TopDocs topDocs) {
		try {
			System.out.println("Total hits: " + topDocs.totalHits);

			long start = System.currentTimeMillis();
			System.out.println("Retrieving name field for all hits....");

			// Report results
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < topDocs.scoreDocs.length; i++) {
				if (printHits) {
					System.out.println("----- Hit " + (i + 1));
					reportHit(searcher, query, topDocs.scoreDocs[i]);
				}

				Document doc = searcher.doc(topDocs.scoreDocs[i].doc);
				String name = doc.get("name");
				sb.append(name);
				sb.append('\n');
			}
			System.out.println(String.format("Done. Took %.3f seconds",
					TimeUtil.secondsSince(start)));

			// Writer w = new FileWriter("S:\\Jan\\names.txt");
			// w.append(sb.toString());
			// w.close();
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	public static void simpleSearch(Directory directory_, Query query) throws IOException {
		IndexReader indexReader = IndexReader.open(directory_);
		IndexSearcher searcher = new IndexSearcher(indexReader);
		simpleSearch(searcher, query);
	}

	public static void simpleSearch(IndexSearcher searcher, Query query) throws IOException {
		if (produceOutput)
			System.out.println("-----\nQuery: " + query + "\n");
		TopDocs topDocs = searcher.search(query, 10000);
		if (produceOutput)
			reportResults(searcher, query, topDocs);
	}

	static void reportHit(IndexSearcher searcher, Query query, ScoreDoc sd) {
		try {
			Document d = searcher.doc(sd.doc);
			List<Fieldable> fields = d.getFields();
			for (Fieldable f : fields) {
				System.out.println(f.name() + " = " + f.stringValue());

			}
			System.out.println(searcher.explain(query, sd.doc));
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static void simpleSearch(Directory directory, String queryString) throws ParseException,
			IOException {
		performSearch(directory, queryString, "contents", false);
	}

	public static void performSearch(Directory directory, String queryString, String defaultField)
			throws ParseException, IOException {
		performSearch(directory, queryString, defaultField, false);
	}

	public static void performSearch(Directory directory, String queryString, String defaultField,
			boolean askQuery) throws ParseException, IOException {
		// Build query
		if (askQuery)
			queryString = ConsoleUtil.askString("Lucene query", queryString);
		QueryParser parser = new QueryParser(Version.LUCENE_30, defaultField,
				new WhitespaceAnalyzer(Version.LUCENE_36));
		Query query = parser.parse(queryString);

		// Search
		LuceneSearchUtil.simpleSearch(directory, query);
	}

	/**
	 * Get the number of unique terms in the index
	 *
	 * @param r
	 *            the index
	 * @return number of terms in index
	 * @throws Exception
	 */
	public static long getTotalUniqueTerms(IndexReader r) throws Exception {
		TermEnum te = r.terms();
		long numberOfTerms = 0;
		while (te.next()) {
			numberOfTerms++;
		}
		te.close();
		return numberOfTerms;
	}
}
