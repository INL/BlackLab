package nl.inl.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.QueryTermExtractor;
import org.apache.lucene.search.highlight.WeightedTerm;
import org.apache.lucene.util.Version;

public class LuceneUtil {

	/**
	* Get all the terms in the index with low edit distance from the supplied term
	 * @param indexReader the index
	* @param term search term
	* @param similarity measure of similarity we need
	* @return the set of terms in the index that are close to our search term
	* @deprecated use version that takes a Lucene fieldname and a collection of words
	*/
	@Deprecated
	public static Set<String> getMatchingTermsFromIndex(IndexReader indexReader, Term term, float similarity) {
		boolean doFuzzy = true;
		if (similarity == 1.0f) {
			// NOTE: even when we don't want to have fuzzy suggestions, we still
			// use a FuzzyQuery, because a TermQuery isn't checked against the index
			// on rewrite, so we won't know if it actually occurs in the index.
			doFuzzy = false;
			similarity = 0.75f;
		}

		FuzzyQuery fq = new FuzzyQuery(term, similarity);
		// TermQuery fq = new TermQuery(term);
		try {
			Query rewritten = fq.rewrite(indexReader);
			WeightedTerm[] wts = QueryTermExtractor.getTerms(rewritten);
			Set<String> terms = new HashSet<String>();
			for (WeightedTerm wt: wts) {
				if (doFuzzy || wt.getTerm().equals(term.text())) {
					terms.add(wt.getTerm());
				}
			}
			return terms;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Test if a term occurs in the index
	 * @param reader the index
	 * @param term the term
	 * @return true iff it occurs in the index
	 */
	public static boolean termOccursInIndex(IndexReader reader, Term term) {
		try {
			return reader.docFreq(term) > 0;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Get all the terms in the index with low edit distance from the supplied term
	 * @param reader the index
	 * @param luceneName
	 *            the field to search in
	 * @param searchTerms
	 *            search terms
	 * @param similarity
	 *            measure of similarity we need
	 * @return the set of terms in the index that are close to our search term
	 * @throws BooleanQuery.TooManyClauses
	 *             if the expansion resulted in too many terms
	 */
	public static Set<String> getMatchingTermsFromIndex(IndexReader reader, String luceneName, Collection<String> searchTerms,
			float similarity) {
		boolean doFuzzy = true;
		if (similarity >= 0.99f) {
			// Exact match; don't use fuzzy query (slow)
			Set<String> result = new HashSet<String>();
			try {
				for (String term : searchTerms) {
					if (reader.docFreq(new Term(luceneName, term)) > 0)
						result.add(term);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return result;
		}

		BooleanQuery q = new BooleanQuery();
		for (String s : searchTerms) {
			FuzzyQuery fq = new FuzzyQuery(new Term(luceneName, s), similarity);
			q.add(fq, Occur.SHOULD);
		}

		try {
			Query rewritten = q.rewrite(reader);
			WeightedTerm[] wts = QueryTermExtractor.getTerms(rewritten);
			Set<String> terms = new HashSet<String>();
			for (WeightedTerm wt : wts) {
				if (doFuzzy || searchTerms.contains(wt.getTerm())) {
					terms.add(wt.getTerm());
				}
			}
			return terms;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Parse a query in the Lucene query language format (QueryParser supplied with Lucene).
	 *
	 * @param luceneQuery the query string
	 * @param defaultField default search field
	 * @return the query
	 * @throws ParseException on syntax error
	 */
	public static Query parseLuceneQuery(String luceneQuery, String defaultField) throws ParseException {
		QueryParser qp = new QueryParser(Version.LUCENE_36, defaultField,
				new StandardAnalyzer(Version.LUCENE_36));
		return qp.parse(luceneQuery);
	}

	/**
	 * Get all words between the specified start and end positions from the term vector.
	 *
	 * NOTE: this may return an array of less than the size requested, if the document ends before
	 * the requested end position.
	 * @param indexReader the index
	 * @param doc
	 *            doc id
	 * @param luceneName
	 *            the index field from which to use the term vector
	 * @param start
	 *            start position (first word we want to request)
	 * @param end
	 *            end position (last word we want to request)
	 * @return the words found, in order
	 */
	public static String[] getWordsFromTermVector(IndexReader indexReader, int doc, String luceneName, int start, int end) {
		try {
			// Vraag de term position vector van de contents van dit document op
			// NOTE: je kunt ook alle termvectors in 1x opvragen. Kan sneller zijn.
			TermPositionVector termPositionVector = (TermPositionVector) indexReader
					.getTermFreqVector(doc, luceneName);
			if (termPositionVector == null) {
				throw new RuntimeException("Field " + luceneName + " has no TermPositionVector");
			}

			// Vraag het array van terms (voor reconstructie text)
			String[] docTerms = termPositionVector.getTerms();

			// Verzamel concordantiewoorden uit term vector
			String[] concordanceWords = new String[end - start + 1];
			int numFound = 0;
			for (int k = 0; k < docTerms.length; k++) {
				int[] positions = termPositionVector.getTermPositions(k);
				for (int l = 0; l < positions.length; l++) {
					int p = positions[l];
					if (p >= start && p <= end) {
						concordanceWords[p - start] = docTerms[k];
						numFound++;
					}
				}
				if (numFound == concordanceWords.length)
					return concordanceWords;
			}
			if (numFound < concordanceWords.length) {
				String[] partial = new String[numFound];
				for (int i = 0; i < numFound; i++) {
					partial[i] = concordanceWords[i];
					if (partial[i] == null) {
						throw new RuntimeException("Not all words found (" + numFound + " out of "
								+ concordanceWords.length
								+ "); missing words in the middle of concordance!");
					}
				}
				return partial;
			}
			return concordanceWords;
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Get all words between the specified start and end positions from the term vector.
	 *
	 * NOTE: this may return an array of less than the size requested, if the document ends before
	 * the requested end position.
	 * @param indexReader the index
	 * @param doc
	 *            doc id
	 * @param luceneName
	 *            the index field from which to use the term vector
	 * @param start
	 *            start position (first word we want to request)
	 * @param end
	 *            end position (last word we want to request)
	 * @return the words found, in order
	 */
	public static List<String[]> getWordsFromTermVector(IndexReader indexReader, int doc, String luceneName, int[] start, int[] end) {
		try {
			// Get the term position vector of the requested field
			TermPositionVector termPositionVector = (TermPositionVector) indexReader
					.getTermFreqVector(doc, luceneName);
			if (termPositionVector == null) {
				throw new RuntimeException("Field " + luceneName + " has no TermPositionVector");
			}

			// Get the array of terms (for reconstructing text)
			String[] docTerms = termPositionVector.getTerms();

			List<String[]> results = new ArrayList<String[]>(start.length);
			for (int i = 0; i < start.length; i++) {
				// Gather concordance words from term vector
				String[] concordanceWords = new String[end[i] - start[i] + 1];
				int numFound = 0;
				for (int k = 0; k < docTerms.length; k++) {
					int[] positions = termPositionVector.getTermPositions(k);
					for (int l = 0; l < positions.length; l++) {
						int p = positions[l];
						if (p >= start[i] && p <= end[i]) {
							concordanceWords[p - start[i]] = docTerms[k];
							numFound++;
						}
					}
					if (numFound == concordanceWords.length)
						break;
				}
				if (numFound < concordanceWords.length) {
					String[] partial = new String[numFound];
					for (int j = 0; j < numFound; j++) {
						partial[j] = concordanceWords[j];
						if (partial[j] == null) {
							throw new RuntimeException("Not all words found (" + numFound
									+ " out of " + concordanceWords.length
									+ "); missing words in the middle of concordance!");
						}
					}
					results.add(partial);
				} else
					results.add(concordanceWords);
			}
			return results;
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
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
