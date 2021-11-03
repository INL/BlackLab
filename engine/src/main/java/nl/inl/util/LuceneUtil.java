package nl.inl.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.LogMergePolicy;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.highlight.QueryTermExtractor;
import org.apache.lucene.search.highlight.WeightedTerm;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.AnnotationSensitivity;

public final class LuceneUtil {

    static final Charset LUCENE_DEFAULT_CHARSET = Charset.forName("utf-8");
    private static final Logger logger = LogManager.getLogger(LuceneUtil.class);

    private LuceneUtil() {
    }

    /**
     * Get all the terms in the index with low edit distance from the supplied term
     *
     * @param reader the index
     * @param luceneName the field to search in
     * @param searchTerms search terms
     * @param maxEdits maximum edit distance (Levenshtein algorithm) for matches
     *            (i.e. lower is more similar)
     * @return the set of terms in the index that are close to our search term
     * @throws BooleanQuery.TooManyClauses if the expansion resulted in too many
     *             terms
     */
    public static Set<String> getMatchingTermsFromIndex(IndexReader reader, String luceneName,
            Collection<String> searchTerms, int maxEdits) {
        boolean doFuzzy = true;
        if (maxEdits == 0) {
            // Exact match; don't use fuzzy query (slow)
            Set<String> result = new HashSet<>();
            try {
                for (String term : searchTerms) {
                    if (reader.docFreq(new Term(luceneName, term)) > 0)
                        result.add(term);
                }
            } catch (IOException e) {
                throw new BlackLabRuntimeException(e);
            }
            return result;
        }

        BooleanQuery.Builder bb = new BooleanQuery.Builder();
        for (String s : searchTerms) {
            FuzzyQuery fq = new FuzzyQuery(new Term(luceneName, s), maxEdits);
            bb.add(fq, Occur.SHOULD);
        }
        BooleanQuery q = bb.build();

        try {
            Query rewritten = q.rewrite(reader);
            WeightedTerm[] wts = QueryTermExtractor.getTerms(rewritten);
            Set<String> terms = new HashSet<>();
            for (WeightedTerm wt : wts) {
                if (doFuzzy || searchTerms.contains(wt.getTerm())) {
                    terms.add(wt.getTerm());
                }
            }
            return terms;
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    /**
     * Parse a query in the Lucene query language format (QueryParser supplied with
     * Lucene).
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
        qp.setAllowLeadingWildcard(true);
        return qp.parse(luceneQuery);
    }

    /**
     * Get all words between the specified start and end positions from the term
     * vector.
     *
     * NOTE: this may return an array of less than the size requested, if the
     * document ends before the requested end position.
     *
     * @param reader the index
     * @param doc doc id
     * @param luceneName the index field from which to use the term vector
     * @param start start position (first word we want to request)
     * @param end end position (last word we want to request)
     * @return the words found, in order
     */
    public static String[] getWordsFromTermVector(IndexReader reader, int doc,
            String luceneName, int start, int end) {
        return getWordsFromTermVector(reader, doc, luceneName, start, end, false);
    }

    /**
     * Get all words between the specified start and end positions from the term
     * vector.
     *
     * NOTE: this may return an array of less than the size requested, if the
     * document ends before the requested end position.
     *
     * @param reader the index
     * @param doc doc id
     * @param luceneName the index field from which to use the term vector
     * @param start start position (first word we want to request)
     * @param end end position (last word we want to request)
     * @param partialOk is it okay if we're missing words in the middle, or do we
     *            need them all? (debug)
     * @return the words found, in order
     */
    public static String[] getWordsFromTermVector(IndexReader reader, int doc,
            String luceneName, int start, int end, boolean partialOk) {

        // Retrieve the term position vector of the contents of this document.
        // NOTE: might be faster to retrieve all term vectors at once

        try {
            Terms terms = reader.getTermVector(doc, luceneName);
            if (terms == null) {
                throw new IllegalArgumentException("Field " + luceneName + " has no Terms");
            }
            if (!terms.hasPositions())
                throw new IllegalArgumentException("Field " + luceneName + " has no character postion information");
            // String[] docTerms = new String[(int) terms.size()];
            // final List<BytesRef> termsList = new ArrayList<BytesRef>();
            TermsEnum termsEnum = terms.iterator();

            // Verzamel concordantiewoorden uit term vector
            PostingsEnum docPosEnum = null;
            int numFound = 0;
            String[] concordanceWords = new String[end - start + 1];
            while (termsEnum.next() != null) {
                docPosEnum = termsEnum.postings(docPosEnum, PostingsEnum.POSITIONS);
                while (docPosEnum.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                    // NOTE: .docId() will always return 0 in this case
                    //if (docPosEnum.docID() != doc)
                    //	throw new BLRuntimeException("Wrong doc id: " + docPosEnum.docID() + " (expected " + doc + ")");
                    for (int i = 0; i < docPosEnum.freq(); i++) {
                        int position = docPosEnum.nextPosition();
                        if (position == -1)
                            throw new BlackLabRuntimeException("Unexpected missing position (i=" + i + ", docPosEnum.freq() = "
                                    + docPosEnum.freq() + ")");
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
                System.arraycopy(concordanceWords, 0, partial, 0, numFound);
                for (int i = 0; i < numFound; i++) {
                    if (partial[i] == null) {
                        throw new BlackLabRuntimeException("Not all words found (" + numFound + " out of "
                                + concordanceWords.length
                                + "); missing words in the middle of concordance!");
                    }
                }
                return partial;
            }
            return concordanceWords;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
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
    public static void getFrequenciesFromTermVector(IndexReader reader, int doc,
            String luceneName, Map<String, Integer> freq) {
        try {
            Terms terms = reader.getTermVector(doc, luceneName);
            if (terms == null) {
                throw new IllegalArgumentException("Field " + luceneName + " has no Terms");
            }
            TermsEnum termsEnum = terms.iterator();

            // Verzamel concordantiewoorden uit term vector
            PostingsEnum postingsEnum = null;
            while (termsEnum.next() != null) {
                postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.FREQS);
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
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Return the list of terms that occur in a field.
     *
     * @param index the index
     * @param fieldName the field
     * @return the matching terms
     */
    public static List<String> getFieldTerms(IndexReader index, String fieldName) {
        return findTermsByPrefix(index, fieldName, null, true, -1);
    }

    /**
     * Return the list of terms that occur in a field.
     *
     * @param index the index
     * @param fieldName the field
     * @param maxResults maximum number to return (or -1 for no limit)
     * @return the matching terms
     */
    public static List<String> getFieldTerms(IndexReader index, String fieldName, int maxResults) {
        return findTermsByPrefix(index, fieldName, null, true, maxResults);
    }

    /**
     * Find terms in the index based on a prefix. Useful for autocomplete. NOTE: no
     * limit on the number of results!
     *
     * @param index the index
     * @param fieldName the field
     * @param prefix the prefix we're looking for
     * @param sensitive match case-sensitively or not?
     * @return the matching terms
     */
    public static List<String> findTermsByPrefix(IndexReader index, String fieldName,
            String prefix, boolean sensitive) {
        return findTermsByPrefix(index, fieldName, prefix, sensitive, -1);
    }
    
    /** Handle a term. */
    @FunctionalInterface
    public interface TermHandler {
    	/** Handle a term.
    	 * 
    	 * @param term term to handle
    	 * @return whether or not to continue iterating over terms.
    	 */
    	boolean term(String term);
    }
    
    /**
     * Find terms in the index based on a prefix. Useful for autocomplete.
     *
     * @param index the index
     * @param fieldName the field to find terms for
     * @param startFrom (prefix of a) term to start iterating from, or null to start at the beginning
     * @param handler called to handle terms found, until it returns false (or we run out of terms)
     * @return the matching terms
     */
    public static void getFieldTerms(IndexReader index, String fieldName, String startFrom, TermHandler handler) {
    	boolean allTerms = startFrom == null || startFrom.length() == 0;
        if (allTerms) {
        	startFrom = "";
        }
        try {
            outerLoop:
            for (LeafReaderContext leafReader : index.leaves()) {
                Terms terms = leafReader.reader().terms(fieldName);
                if (terms == null) {
                    if (logger.isDebugEnabled())
                        logger.debug("no terms for field " + fieldName + " in leafReader, skipping");
                    continue;
                }
                TermsEnum termsEnum = terms.iterator();
                BytesRef brPrefix = new BytesRef(startFrom.getBytes(LUCENE_DEFAULT_CHARSET));
                TermsEnum.SeekStatus seekStatus = termsEnum.seekCeil(brPrefix);

                if (seekStatus == TermsEnum.SeekStatus.END) {
                    continue;
                }
                for (BytesRef term = termsEnum.term(); term != null; term = termsEnum.next()) {
                    String termText = term.utf8ToString();
                    if (!handler.term(termText))
                    	break outerLoop;
                }
            }
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    /**
     * Find terms in the index based on a prefix. Useful for autocomplete.
     *
     * @param index the index
     * @param fieldName the field
     * @param prefix the prefix we're looking for (null or empty string for all
     *            terms)
     * @param sensitive match case-sensitively or not?
     * @param maxResults max. number of results to return (or -1 for all)
     * @return the matching terms
     */
    public static List<String> findTermsByPrefix(IndexReader index, String fieldName,
            String prefix, boolean sensitive, int maxResults) {
        boolean allTerms = prefix == null || prefix.length() == 0;
        if (allTerms) {
            prefix = "";
            sensitive = true; // don't do unnecessary work in this case
        }
        try {
            if (!sensitive)
                prefix = StringUtil.stripAccents(prefix).toLowerCase();
            Set<String> results = new TreeSet<>();
            
            outerLoop:
            for (LeafReaderContext leafReader : index.leaves()) {
                Terms terms = leafReader.reader().terms(fieldName);
                if (terms == null) {
                    if (logger.isDebugEnabled())
                        logger.debug("no terms for field " + fieldName + " in leafReader, skipping");
                    continue;
                }
                TermsEnum termsEnum = terms.iterator();
                BytesRef brPrefix = new BytesRef(prefix.getBytes(LUCENE_DEFAULT_CHARSET));
                TermsEnum.SeekStatus seekStatus = termsEnum.seekCeil(brPrefix);

                if (seekStatus == TermsEnum.SeekStatus.END) {
                    continue;
                }
                for (BytesRef term = termsEnum.term(); term != null; term = termsEnum.next()) {
                    if (maxResults > 0 && results.size() > maxResults)
                        break outerLoop;
                    
                    String termText = term.utf8ToString();
                    boolean startsWithPrefix = allTerms ? true : sensitive ? StringUtil.stripAccents(termText).startsWith(prefix)
                            : termText.startsWith(prefix);
                    if (!startsWithPrefix) {
                        // Doesn't match prefix or different field; no more matches
                        break;
                    }
                    // Match, add term
                    results.add(termText);
                }
            }
            
            return new ArrayList<>(results);
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    /**
     * Get term frequencies for an annotation in a subset of documents.
     *
     * @param indexSearcher
     * @param documentFilterQuery document filter, or null for all documents
     * @param annotSensitivity field to get frequencies for
     * @param searchTerms list of terms to get frequencies for, or null for all terms
     * @return term frequencies
     */
    public static Map<String, Integer> termFrequencies(IndexSearcher indexSearcher, Query documentFilterQuery,
            AnnotationSensitivity annotSensitivity, Set<String> searchTerms) {
        try {
            Map<String, Integer> freq = new HashMap<>();
            IndexReader indexReader = indexSearcher.getIndexReader();
            String field = annotSensitivity.luceneField();

            Weight weight = null;
            if (documentFilterQuery != null) {
                weight = indexSearcher.createNormalizedWeight(documentFilterQuery, false);
                if (weight == null)
                    throw new BlackLabRuntimeException("weight == null");
            }

            for (LeafReaderContext arc : indexReader.leaves()) {
                if (arc == null)
                    throw new BlackLabRuntimeException("arc == null");
                if (arc.reader() == null)
                    throw new BlackLabRuntimeException("arc.reader() == null");

                LeafReader reader = arc.reader();
                if (weight != null) { // retrieve term frequency per matched document
                    Scorer scorer = weight.scorer(arc);
                    if (scorer == null) { // no matched documents
                        continue;
                    }
                    DocIdSetIterator documentIterator = scorer.iterator();
                    int doc;
                    while ((doc = documentIterator.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS) {
                        Terms terms = reader.getTermVector(doc, field);
                        if (terms == null) {
                            throw new IllegalArgumentException("Field " + field + " has no Terms");
                        }

                        getTermFrequencies(terms.iterator(), searchTerms, freq);
                    }
                } else {
                    // all documents - use the fast path
                    getTermFrequencies(reader.terms(field).iterator(), searchTerms, freq);
                }
            }

            return freq;
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     * Get the frequency of all terms in the TermsEnum or the frequency list and add them to the freq map.
     *
     * @param it the terms in the (set of) documents or a leaf
     * @param searchTerms list of terms whose frequencies to retrieve, or null/empty to retrieve for all terms
     * @param freq map containing existing frequencies to add on to or merge in to
     * @return the freq map, for ease of use
     * @throws IOException
     */
    private static Map<String, Integer> getTermFrequencies(TermsEnum it, Set<String> searchTerms, Map<String, Integer> freq) throws IOException {
        if (searchTerms != null && !searchTerms.isEmpty()) {
            for (String term : searchTerms) {
                if (it.seekExact(new BytesRef(term))) {
                    if (freq.containsKey(term)) {
                        freq.put(term, (int) (freq.get(term) + it.totalTermFreq()));
                    } else {
                        freq.put(term, (int) it.totalTermFreq());
                    }
                } else {
                    if (!freq.containsKey(term)) {
                        freq.put(term, 0);
                    }
                }
            }
        } else {
            BytesRef cur = null;
            while ((cur = it.next()) != null) {
                String term = cur.utf8ToString();
                if (freq.containsKey(term)) {
                    freq.put(term, (int) (freq.get(term) + it.totalTermFreq()));
                } else {
                    freq.put(term, (int) it.totalTermFreq());
                }
            }
        }
        return freq;
    }

    public static IndexWriterConfig getIndexWriterConfig(Analyzer analyzer, boolean create) {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setOpenMode(create ? OpenMode.CREATE : OpenMode.CREATE_OR_APPEND);
        config.setRAMBufferSizeMB(150); // faster indexing

        // Set merge factor (if using LogMergePolicy, which is the default up to version LUCENE_32,
        // so yes)
        MergePolicy mp = config.getMergePolicy();
        if (mp instanceof LogMergePolicy) {
            ((LogMergePolicy) mp).setMergeFactor(40); // faster indexing
        }
        return config;
    }

    public static long getSumTotalTermFreq(IndexReader reader, String luceneField) {
        long totalTerms = 0;
        try {
            for (LeafReaderContext ctx : reader.leaves()) {
                Terms terms = ctx.reader().terms(luceneField);
                if (terms == null) {
                    // if this LeafReader doesn't include this field, just skip it
                    continue;
                }
//				if (terms == null)
//					throw new BLRuntimeException("Field " + luceneField + " does not exist!");
                totalTerms += terms.getSumTotalTermFreq();
            }
            return totalTerms;
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    /**
     * Enumerate all the terms in the given Lucene field, collecting all the
     * subproperty names and values. Usually used for part of speech, where all the
     * features are stored as separate subproperties.
     *
     * @param index our index
     * @param fieldName field in the Lucene index to enumerate terms from
     * @return subproperties and their values
     */
    public static Map<String, Set<String>> getOldSingleFieldSubprops(IndexReader index, String fieldName) {
        Map<String, Set<String>> results = new TreeMap<>();
        try {
            for (LeafReaderContext leafReader : index.leaves()) {
                Terms terms = leafReader.reader().terms(fieldName);
                if (terms == null) {
                    // if this LeafReader doesn't include this field, just skip it
                    continue;
                }
                TermsEnum termsEnum = terms.iterator();
                while (true) {
                    BytesRef term = termsEnum.next();
                    if (term == null)
                        break;
                    String termText = term.utf8ToString();
                    if (termText.contains(AnnotatedFieldNameUtil.SUBANNOTATION_SEPARATOR)) {
                        termText = StringUtil.stripAccents(termText).toLowerCase();
                        String[] parts = termText.split(AnnotatedFieldNameUtil.SUBANNOTATION_SEPARATOR);
                        String subpropName = parts[1];
                        Set<String> resultList = results.get(subpropName);
                        if (resultList == null) {
                            resultList = new TreeSet<>();
                            results.put(subpropName, resultList);
                        }
                        String subpropValue = parts[2];
                        resultList.add(subpropValue);
                    }
                }
            }
            return results;
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    /**
     * Return the maximum number of terms in any one LeafReader.
     *
     * This is a measure of the number of unique terms in the index for the specified field.
     *
     * It is not an exact number. We only use this to enable/disable certain optimizations for certain fields.
     *
     * @param reader index reader
     * @param fieldName Lucene field name
     * @return maximum number of terms in a LeafReader
     */
    public static long getMaxTermsPerLeafReader(IndexReader reader, String fieldName) {
        long maxTermsPerLeafReader = 0;
        try {
            for (LeafReaderContext leafReader : reader.leaves()) {
                Terms terms = leafReader.reader().terms(fieldName);
                if (maxTermsPerLeafReader < terms.size())
                    maxTermsPerLeafReader = terms.size();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return maxTermsPerLeafReader;
    }
}
