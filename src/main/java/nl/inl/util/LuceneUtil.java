package nl.inl.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.highlight.QueryTermExtractor;
import org.apache.lucene.search.highlight.WeightedTerm;
import org.apache.lucene.util.BytesRef;

public class LuceneUtil {

	private LuceneUtil() {
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
	 * @param maxEdits
	 *            maximum edit distance (Levenshtein algorithm) for matches
	 *            (i.e. lower is more similar)
	 * @return the set of terms in the index that are close to our search term
	 * @throws BooleanQuery.TooManyClauses
	 *             if the expansion resulted in too many terms
	 */
	public static Set<String> getMatchingTermsFromIndex(IndexReader reader, String luceneName,
			Collection<String> searchTerms, int maxEdits) {
		boolean doFuzzy = true;
		if (maxEdits == 0) {
			// Exact match; don't use fuzzy query (slow)
			Set<String> result = new HashSet<>();
			try {
				for (String term: searchTerms) {
					if (reader.docFreq(new Term(luceneName, term)) > 0)
						result.add(term);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			return result;
		}

		BooleanQuery q = new BooleanQuery();
		for (String s: searchTerms) {
			FuzzyQuery fq = new FuzzyQuery(new Term(luceneName, s), maxEdits);
			q.add(fq, Occur.SHOULD);
		}

		try {
			Query rewritten = q.rewrite(reader);
			WeightedTerm[] wts = QueryTermExtractor.getTerms(rewritten);
			Set<String> terms = new HashSet<>();
			for (WeightedTerm wt: wts) {
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
	 * @param analyzer analyzer to use
	 * @param defaultField default search field
	 * @return the query
	 * @throws ParseException on syntax error
	 */
	public static Query parseLuceneQuery(String luceneQuery, Analyzer analyzer, String defaultField)
			throws ParseException {
		QueryParser qp = new QueryParser(defaultField, analyzer);
		return qp.parse(luceneQuery);
	}

	/**
	 * Get all words between the specified start and end positions from the term vector.
	 *
	 * NOTE: this may return an array of less than the size requested, if the document ends before
	 * the requested end position.
	 * @param reader the index
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
	public static String[] getWordsFromTermVector(DirectoryReader reader, int doc,
			String luceneName, int start, int end) {
		return getWordsFromTermVector(reader, doc, luceneName, start, end, false);
	}

	/**
	 * Get all words between the specified start and end positions from the term vector.
	 *
	 * NOTE: this may return an array of less than the size requested, if the document ends before
	 * the requested end position.
	 * @param reader the index
	 * @param doc
	 *            doc id
	 * @param luceneName
	 *            the index field from which to use the term vector
	 * @param start
	 *            start position (first word we want to request)
	 * @param end
	 *            end position (last word we want to request)
	 * @param partialOk
	 *   is it okay if we're missing words in the middle, or do we need them all?
	 *   (debug)
	 * @return the words found, in order
	 */
	public static String[] getWordsFromTermVector(DirectoryReader reader, int doc,
			String luceneName, int start, int end, boolean partialOk) {

		// Retrieve the term position vector of the contents of this document.
		// NOTE: might be faster to retrieve all term vectors at once

		try {
			org.apache.lucene.index.Terms terms = reader.getTermVector(doc, luceneName);
			if (terms == null) {
				throw new RuntimeException("Field " + luceneName + " has no Terms");
			}
			if (!terms.hasPositions())
				throw new RuntimeException("Field " + luceneName + " has no character postion information");
			// String[] docTerms = new String[(int) terms.size()];
			// final List<BytesRef> termsList = new ArrayList<BytesRef>();
			TermsEnum termsEnum = terms.iterator();

			// Verzamel concordantiewoorden uit term vector
			PostingsEnum docPosEnum = null;
			int numFound = 0;
			String[] concordanceWords = new String[end - start + 1];
			while (termsEnum.next() != null) {
				docPosEnum = termsEnum.postings(null, docPosEnum, PostingsEnum.POSITIONS);
				while (docPosEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
					// NOTE: .docId() will always return 0 in this case
					//if (docPosEnum.docID() != doc)
					//	throw new RuntimeException("Wrong doc id: " + docPosEnum.docID() + " (expected " + doc + ")");
					int position = -1;
					for (int i = 0; i < docPosEnum.freq(); i++)  {
						position = docPosEnum.nextPosition();
						if (position == -1)
							throw new RuntimeException("Unexpected missing position (i=" + i + ", docPosEnum.freq() = " + docPosEnum.freq() + ")");
						if (position >= start && position <= end) {
							if (concordanceWords[position - start] == null)
								concordanceWords[position - start] = termsEnum.term().utf8ToString();
							else
								concordanceWords[position - start] += "|" + termsEnum.term().utf8ToString();
							numFound++;
						}
					}
					if (numFound == concordanceWords.length)
						return concordanceWords;
				}
			}

			if (numFound < concordanceWords.length && !partialOk) {
				// If we simply ran into the end of the document, that's okay;
				// but if words are missing in the middle, that's not.
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
	 * Add term frequencies for a single document to a frequency map.
	 *
	 * @param reader the index
	 * @param doc doc id
	 * @param luceneName the index field from which to use the term vector
	 * @param freq where to add to the token frequencies
	 */
	public static void getFrequenciesFromTermVector(DirectoryReader reader, int doc,
			String luceneName, Map<String, Integer> freq) {
		try {
			org.apache.lucene.index.Terms terms = reader.getTermVector(doc, luceneName);
			if (terms == null) {
				throw new RuntimeException("Field " + luceneName + " has no Terms");
			}
			TermsEnum termsEnum = terms.iterator();

			// Verzamel concordantiewoorden uit term vector
			PostingsEnum postingsEnum = null;
			while (termsEnum.next() != null) {
				postingsEnum = termsEnum.postings(null, postingsEnum, PostingsEnum.FREQS);
				String term = termsEnum.term().utf8ToString();
				Integer n = freq.get(term);
				if (n == null) {
					n = 0;
				}
				while (postingsEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
					n += termsEnum.docFreq();
				}
				freq.put(term, n);
			}
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Return the list of terms that occur in a field.
	 * @param index the index
	 * @param fieldName the field
	 * @return the matching terms
	 */
	public static List<String> getFieldTerms(LeafReader index, String fieldName) {
		return findTermsByPrefix(index, fieldName, null, true, -1);
	}

	/**
	 * Return the list of terms that occur in a field.
	 * @param index the index
	 * @param fieldName the field
	 * @param maxResults maximum number to return (or -1 for no limit)
	 * @return the matching terms
	 */
	public static List<String> getFieldTerms(LeafReader index, String fieldName, int maxResults) {
		return findTermsByPrefix(index, fieldName, null, true, maxResults);
	}

	/**
	 * Find terms in the index based on a prefix. Useful for autocomplete.
	 * @param index the index
	 * @param fieldName the field
	 * @param prefix the prefix we're looking for
	 * @param sensitive match case-sensitively or not?
	 * @return the matching terms
	 */
	public static List<String> findTermsByPrefix(LeafReader index, String fieldName,
			String prefix, boolean sensitive) {
		return findTermsByPrefix(index, fieldName, prefix, sensitive, -1);
	}

	/**
	 * Find terms in the index based on a prefix. Useful for autocomplete.
	 * @param index the index
	 * @param fieldName the field
	 * @param prefix the prefix we're looking for (null or empty string for all terms)
	 * @param sensitive match case-sensitively or not?
	 * @param maxResults max. number of results to return (or -1 for all)
	 * @return the matching terms
	 */
	public static List<String> findTermsByPrefix(LeafReader index, String fieldName,
			String prefix, boolean sensitive, int maxResults) {
		boolean allTerms = prefix == null || prefix.length() == 0;
		if (allTerms) {
			prefix = "";
			sensitive = true; // don't do unnecessary work in this case
		}
		try {
			if (!sensitive)
				prefix = StringUtil.removeAccents(prefix).toLowerCase();
			org.apache.lucene.index.Terms terms = index.terms(fieldName);
			List<String> results = new ArrayList<>();
			TermsEnum termsEnum = terms.iterator();
			BytesRef brPrefix = new BytesRef(prefix.getBytes("utf-8"));
			termsEnum.seekCeil(brPrefix); // find the prefix in the terms list
			while (maxResults < 0 || results.size() < maxResults) {
				BytesRef term = termsEnum.next();
				if (term == null)
					break;
				String termText = term.utf8ToString();
				String optDesensitized = termText;
				if (!sensitive)
					optDesensitized = StringUtil.removeAccents(termText).toLowerCase();
				if (!allTerms && !optDesensitized.substring(0, prefix.length()).equalsIgnoreCase(prefix)) {
					// Doesn't match prefix or different field; no more matches
					break;
				}
				// Match, add term
				results.add(termText);
			}
			return results;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

}
