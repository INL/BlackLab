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
package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import nl.inl.blacklab.externalstorage.ContentAccessorContentStore;
import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.externalstorage.ContentStoreDir;
import nl.inl.blacklab.externalstorage.ContentStoreDirAbstract;
import nl.inl.blacklab.externalstorage.ContentStoreDirUtf8;
import nl.inl.blacklab.externalstorage.ContentStoreDirZip;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.highlight.XmlHighlighter;
import nl.inl.blacklab.highlight.XmlHighlighter.HitSpan;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.IndexStructure.ComplexFieldDesc;
import nl.inl.blacklab.search.IndexStructure.PropertyDesc;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.lucene.SpansFiltered;
import nl.inl.blacklab.search.lucene.TextPatternTranslatorSpanQuery;
import nl.inl.util.ExUtil;
import nl.inl.util.StringUtil;
import nl.inl.util.VersionFile;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.index.TermVectorOffsetInfo;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.highlight.QueryTermExtractor;
import org.apache.lucene.search.highlight.WeightedTerm;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.store.FSDirectory;

/**
 * The main interface into the BlackLab library. The Searcher object is instantiated with an open
 * Lucene IndexReader and accesses that index through special methods.
 *
 * The Searcher object knows how to access the original contents of indexed fields, either because
 * the field is a stored field in the Lucene index, or because it knows where else the content can
 * be found (such as in fixed-length-encoding files, for fast random access).
 *
 * Searcher is thread-safe: a single instance may be shared to perform a number of simultaneous
 * searches.
 */
public class Searcher {

	protected static final Logger logger = Logger.getLogger(Searcher.class);

	/** Complex field name for default contents field */
	public static final String DEFAULT_CONTENTS_FIELD_NAME = "contents";

	/** The collator to use for sorting. Defaults to English collator. */
	private Collator collator = Collator.getInstance(new Locale("en", "GB"));

	/**
	 * ContentAccessors tell us how to get a field's content:
	 * <ol>
	 * <li>if there is no contentaccessor: get it from the Lucene index (stored field)</li>
	 * <li>from an external source (file, database) if it's not (because the content is very large
	 * and/or we want faster random access to the content than a stored field can provide)</li>
	 * </ol>
	 *
	 * Indexed by complex field name.
	 */
	private Map<String, ContentAccessor> contentAccessors = new HashMap<String, ContentAccessor>();

	/**
	 * ForwardIndices allow us to quickly find what token occurs at a specific position. This speeds
	 * up grouping and sorting. There may be several indices on a complex field, e.g.: word form,
	 * lemma, part of speech.
	 *
	 * Indexed by property name.
	 */
	private Map<String, ForwardIndex> forwardIndices = new HashMap<String, ForwardIndex>();

	/**
	 * The Lucene index reader
	 */
	private IndexReader indexReader;

	/**
	 * The Lucene IndexSearcher, for dealing with non-Span queries (for per-document scoring)
	 */
	private IndexSearcher indexSearcher;

	/**
	 * Name of the main contents field (used as default parameter value for many methods)
	 */
	public String fieldNameContents;

	/** Default number of words around a hit */
	private int defaultContextSize = 5;

	/** Should we default to case-sensitive searching? [false] */
	private boolean defaultCaseSensitive = false;

	/** Should we default to diacritics-sensitive searching? [false] */
	private boolean defaultDiacriticsSensitive = false;

	/**
	 * Directory where our index resides
	 */
	private File indexLocation;

	/** Structure of our index */
	private IndexStructure indexStructure;

	/** Do we want to retrieve concordances from the forward index instead of from the
	 *  content store? This may be more efficient, particularly for small result sets
	 *  (because it eliminates seek time and decompression time).
	 *
	 *  By default, this is set to true iff a punctuation forward index is present.
	 */
	private boolean concordancesFromForwardIndex = false;

	/** Forward index to use as text context of &lt;w/&gt; tags in concordances (words; null = no text content) */
	String concWordFI = "word";

	/** Forward index to use as text context between &lt;w/&gt; tags in concordances (punctuation+whitespace; null = just a space) */
	String concPunctFI = ComplexFieldUtil.PUNCTUATION_PROP_NAME;

	/** Forward indices to use as attributes of &lt;w/&gt; tags in concordances (null = the rest) */
	Collection<String> concAttrFI = null; // all other FIs are attributes

	/**
	 * Are we making concordances using the forward index (true) or using
	 * the content store (false)? Forward index is more efficient but returns
	 * concordances that don't include XML tags.
	 *
	 * @return true iff we use the forward index for making concordances.
	 */
	public boolean getMakeConcordancesFromForwardIndex() {
		return concordancesFromForwardIndex;
	}

	/**
	 * Do our concordances include the original XML tags, or are they stripped out?
	 * Concordances made from the content store do include tags; those made from
	 * the forward index do not.
	 *
	 * @return true iff our concordances include XML tags.
	 */
	public boolean concordancesIncludeXmlTags() {
		// @@@ TODO experimental, should be parameterized
		String lemmaField = ComplexFieldUtil.propertyField(fieldNameContents, "lemma");
		return !concordancesFromForwardIndex || forwardIndices.containsKey(lemmaField);
	}

	/**
	 * Do we want to retrieve concordances from the forward index instead of from the
	 * content store? This may be more efficient, particularly for small result sets
	 * (because it eliminates seek time and decompression time), but concordances won't
	 * include XML tags.
	 *
	 * Also, if there is no punctuation forward index ("punct"), concordances won't include
	 * punctuation.
	 *
	 * @param concordancesFromForwardIndex true if we want to use the forward index to make
	 * concordances.
	 */
	public void setMakeConcordancesFromForwardIndex(boolean concordancesFromForwardIndex) {
		this.concordancesFromForwardIndex = concordancesFromForwardIndex;
	}

	/**
	 * Construct a Searcher object. Note that using this constructor, the Searcher is responsible
	 * for opening and closing the Lucene index, forward index and content store.
	 *
	 * Automatically detects and uses forward index and content store if available.
	 *
	 * @param indexDir
	 *            the index directory
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	public Searcher(File indexDir) throws CorruptIndexException, IOException {
		if (!VersionFile.isTypeVersion(indexDir, "blacklab", "1")
				&& !VersionFile.isTypeVersion(indexDir, "blacklab", "2"))
			throw new RuntimeException("BlackLab index has wrong type or version! "
					+ VersionFile.report(indexDir));

		logger.debug("Constructing Searcher...");

		// Open Lucene index
		indexReader = IndexReader.open(FSDirectory.open(indexDir));

		// Determine the index structure
		indexStructure = new IndexStructure(indexReader);

		// Detect and open the ContentStore for the contents field
		this.indexLocation = indexDir;
		this.fieldNameContents = indexStructure.getMainContentsField().getName();

		// See if we have a punctuation forward index. If we do,
		// default to creating concordances using that.
		if (indexStructure.getMainContentsField().hasPunctuation()) {
			concordancesFromForwardIndex = true;
		}

		// Register content stores
		for (String cfn: indexStructure.getComplexFields()) {
			if (indexStructure.getComplexFieldDesc(cfn).hasContentStore()) {
				File dir = new File(indexDir, "cs_" + cfn);
				if (!dir.exists()) {
					dir = new File(indexDir, "xml");
				}
				if (dir.exists()) {
					registerContentStore(fieldNameContents, openContentStore(dir));
				}
			}
		}

		init();
		logger.debug("Done.");
	}

	/**
	 * Construct the IndexSearcher and set the maximum boolean clause count a little higher.
	 */
	private void init() {
		indexSearcher = new IndexSearcher(indexReader);

		// Make sure large wildcard/regex expansions succeed
		BooleanQuery.setMaxClauseCount(100000);

		// Open the forward indices
		openForwardIndices();
	}

	/**
	 * Finalize the Searcher object. This closes the IndexSearcher and (depending on the constructor
	 * used) may also close the index reader.
	 */
	public void close() {
		try {
			indexSearcher.close();
			indexReader.close();

			// Close the forward indices
			for (ForwardIndex fi : forwardIndices.values()) {
				fi.close();
			}

			// Close the content accessor(s)
			// (the ContentStore, and possibly other content accessors
			// (although that feature is not used right now))
			for (ContentAccessor ca : contentAccessors.values()) {
				ca.close();
			}

		} catch (IOException e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Get information about the structure of the BlackLab index.
	 *
	 * @return the structure object
	 */
	public IndexStructure getIndexStructure() {
		return indexStructure;
	}

	/**
	 * Retrieve a Lucene Document object from the index.
	 *
	 * @param doc
	 *            the document id
	 * @return the Lucene Document
	 */
	public Document document(int doc) {
		try {
			return indexReader.document(doc);
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	public SpanQuery filterDocuments(SpanQuery query, Filter filter) {
		try {
			return new SpanQueryFiltered(query, filter, indexReader);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public SpanQuery filterDocuments(SpanQuery query, DocIdSet docIdSet) {
		return new SpanQueryFiltered(query, docIdSet);
	}

	/**
	 * Filter a Spans object (collection of hits), only keeping hits in a subset of documents,
	 * described by a Filter. All other hits are discarded.
	 *
	 * @param spans
	 *            the collection of hits
	 * @param filter
	 *            the document filter
	 * @return the resulting Spans
	 */
	public Spans filterDocuments(Spans spans, Filter filter) {
		try {
			return new SpansFiltered(spans, filter, indexReader);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public SpanQuery createSpanQuery(TextPattern pattern, String fieldName, DocIdSet docIdSet) {
		// Convert to SpanQuery
		pattern = pattern.rewrite();
		TextPatternTranslatorSpanQuery spanQueryTranslator = new TextPatternTranslatorSpanQuery();
		SpanQuery spanQuery = pattern.translate(spanQueryTranslator,
				getDefaultExecutionContext(fieldName));

		if (docIdSet != null) {
			spanQuery = new SpanQueryFiltered(spanQuery, docIdSet);
		}
		return spanQuery;
	}

	public SpanQuery createSpanQuery(TextPattern pattern, DocIdSet docIdSet) {
		return createSpanQuery(pattern, fieldNameContents, docIdSet);
	}

	public SpanQuery createSpanQuery(TextPattern pattern, String fieldName, Filter filter) {
		try {
			return createSpanQuery(pattern, fieldName,
					filter == null ? null : filter.getDocIdSet(indexReader));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public SpanQuery createSpanQuery(TextPattern pattern, Filter filter) {
		return createSpanQuery(pattern, fieldNameContents, filter);
	}

	public SpanQuery createSpanQuery(TextPattern pattern, String fieldName) {
		return createSpanQuery(pattern, fieldName, (DocIdSet) null);
	}

	public SpanQuery createSpanQuery(TextPattern pattern) {
		return createSpanQuery(pattern, fieldNameContents, (DocIdSet) null);
	}

	/**
	 * Find hits for a pattern in a field.
	 *
	 * @param query
	 *            the pattern to find
	 * @param fieldNameConc
	 *            field to use for concordances
	 * @return the hits found
	 * @throws BooleanQuery.TooManyClauses
	 *             if a wildcard or regular expression term is overly broad
	 */
	public Hits find(SpanQuery query, String fieldNameConc) throws BooleanQuery.TooManyClauses {
		return new Hits(this, fieldNameConc, query);
	}

	/**
	 * Find hits for a pattern in a field.
	 *
	 * @param query
	 *            the pattern to find
	 * @return the hits found
	 * @throws BooleanQuery.TooManyClauses
	 *             if a wildcard or regular expression term is overly broad
	 */
	public Hits find(SpanQuery query) throws BooleanQuery.TooManyClauses {
		return new Hits(this, fieldNameContents, query);
	}

	/**
	 * Find hits for a pattern in a field.
	 *
	 * @param pattern
	 *            the pattern to find
	 * @param fieldName
	 *            field to use for sorting and displaying resulting concordances.
	 * @param filter
	 *            determines which documents to search
	 *
	 * @return the hits found
	 * @throws BooleanQuery.TooManyClauses
	 *             if a wildcard or regular expression term is overly broad
	 */
	public Hits find(TextPattern pattern, String fieldName, Filter filter)
			throws BooleanQuery.TooManyClauses {
		return new Hits(this, fieldName, createSpanQuery(pattern, fieldName, filter));
	}

	/**
	 * Find hits for a pattern and filter them.
	 *
	 * @param pattern
	 *            the pattern to find
	 * @param filter
	 *            determines which documents to search
	 *
	 * @return the hits found
	 * @throws BooleanQuery.TooManyClauses
	 *             if a wildcard or regular expression term is overly broad
	 */
	public Hits find(TextPattern pattern, Filter filter) throws BooleanQuery.TooManyClauses {
		return find(pattern, fieldNameContents, filter);
	}

	/**
	 * Find hits for a pattern in a field.
	 *
	 * @param pattern
	 *            the pattern to find
	 * @param fieldName
	 *            which field to find the pattern in
	 *
	 * @return the hits found
	 * @throws BooleanQuery.TooManyClauses
	 *             if a wildcard or regular expression term is overly broad
	 */
	public Hits find(TextPattern pattern, String fieldName) throws BooleanQuery.TooManyClauses {
		return find(pattern, fieldName, null);
	}

	/**
	 * Find hits for a pattern.
	 *
	 * @param pattern
	 *            the pattern to find
	 *
	 * @return the hits found
	 * @throws BooleanQuery.TooManyClauses
	 *             if a wildcard or regular expression term is overly broad
	 */
	public Hits find(TextPattern pattern) throws BooleanQuery.TooManyClauses {
		return find(pattern, fieldNameContents, null);
	}

	/**
	 * Find matching documents and their scores for a pattern.
	 *
	 * You can pass in both a SpanQuery or a regular Query.
	 *
	 * @param q
	 * @return object that can iterate over matching docs and provide their scores. NOTE: null can
	 *         be returned if there were no matches!
	 * @throws BooleanQuery.TooManyClauses
	 *             if a wildcard or regular expression term is overly broad
	 */
	public Scorer findDocScores(Query q) {
		try {
			IndexSearcher s = new IndexSearcher(indexReader); // TODO: cache in field?
			try {
				Weight w = s.createNormalizedWeight(q);
				Scorer sc = w.scorer(indexReader, true, false);
				return sc;
			} finally {
				s.close();
			}
		} catch (IOException e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Find the top-scoring documents.
	 *
	 * @param q
	 *            the query
	 *
	 * @param n
	 *            number of top documents to return
	 * @return the documents
	 */
	public TopDocs findTopDocs(Query q, int n) {
		try {
			IndexSearcher s = new IndexSearcher(indexReader);
			try {
				return s.search(q, n);
			} finally {
				s.close();
			}
		} catch (IOException e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Get character positions from word positions.
	 *
	 * Places character positions in the same arrays as the word positions were specified in.
	 *
	 * NOTE: If any illegal word positions are specified (say, past the end of the document), a sane
	 * default value is chosen (in this case, the last character of the last word found).
	 *
	 * @param doc
	 *            the document from which to find character positions
	 * @param fieldName
	 *            the field from which to find character positions
	 * @param startsOfWords
	 *            word positions for which we want starting character positions (i.e. the position
	 *            of the first letter of that word)
	 * @param endsOfWords
	 *            word positions for which we want ending character positions (i.e. the position of
	 *            the last letter of that word)
	 */
	public void getCharacterOffsets(int doc, String fieldName, int[] startsOfWords,
			int[] endsOfWords) {
		getCharacterOffsets(doc, fieldName, startsOfWords, endsOfWords, true);
	}

	/**
	 * Get character positions from word positions.
	 *
	 * Places character positions in the same arrays as the word positions were specified in.
	 *
	 * @param doc
	 *            the document from which to find character positions
	 * @param fieldName
	 *            the field from which to find character positions
	 * @param startsOfWords
	 *            word positions for which we want starting character positions (i.e. the position
	 *            of the first letter of that word)
	 * @param endsOfWords
	 *            word positions for which we want ending character positions (i.e. the position of
	 *            the last letter of that word)
	 * @param fillInDefaultsIfNotFound
	 *            if true, if any illegal word positions are specified (say, past the end of the
	 *            document), a sane default value is chosen (in this case, the last character of the
	 *            last word found). Otherwise, throws an exception.
	 */
	public void getCharacterOffsets(int doc, String fieldName, int[] startsOfWords,
			int[] endsOfWords, boolean fillInDefaultsIfNotFound) {
		String fieldPropName = ComplexFieldUtil.mainPropertyOffsetsField(indexStructure, fieldName);
		TermFreqVector termFreqVector = getTermFreqVector(doc, fieldPropName);
		if (!(termFreqVector instanceof TermPositionVector)) {
			throw new RuntimeException("Field has no character position information!");
		}
		TermPositionVector termPositionVector = (TermPositionVector) termFreqVector;

		int numStarts = startsOfWords.length;
		int numEnds = endsOfWords.length;
		int total = numStarts + numEnds;
		int[] done = new int[total]; // NOTE: array is automatically initialized to zeroes!

		// Vraag het array van terms (voor reconstructie text)
		String[] docTerms = termPositionVector.getTerms();

		// Determine lowest and highest word position we'd like to know something about.
		// This saves a little bit of time for large result sets.
		int minP = -1, maxP = -1;
		for (int i = 0; i < startsOfWords.length; i++) {
			if (startsOfWords[i] < minP || minP == -1)
				minP = startsOfWords[i];
			if (startsOfWords[i] > maxP)
				maxP = startsOfWords[i];
		}
		for (int i = 0; i < endsOfWords.length; i++) {
			if (endsOfWords[i] < minP || minP == -1)
				minP = endsOfWords[i];
			if (endsOfWords[i] > maxP)
				maxP = endsOfWords[i];
		}
		if (minP < 0 || maxP < 0)
			throw new RuntimeException("Can't determine min and max positions");

		// Verzamel concordantiewoorden uit term vector
		int found = 0;
		int lowestPos = -1, highestPos = -1;
		int lowestPosFirstChar = -1, highestPosLastChar = -1;
		for (int k = 0; k < docTerms.length && found < total; k++) {
			int[] positions = termPositionVector.getTermPositions(k);
			TermVectorOffsetInfo[] offsetInfo = termPositionVector.getOffsets(k);
			for (int l = 0; l < positions.length; l++) {
				int p = positions[l];

				// Keep track of the lowest and highest char pos, so
				// we can fill in the character positions we didn't find
				if (p < lowestPos || lowestPos == -1) {
					lowestPos = p;
					lowestPosFirstChar = offsetInfo[l].getStartOffset();
				}
				if (p > highestPos) {
					highestPos = p;
					highestPosLastChar = offsetInfo[l].getEndOffset();
				}

				// We've calculated the min and max word positions in advance, so
				// we know we can skip this position if it's outside the range we're interested in.
				// (Saves a little time for large result sets)
				if (p < minP || p > maxP)
					continue;

				for (int m = 0; m < numStarts; m++) {
					if (done[m] == 0 && p == startsOfWords[m]) {
						done[m] = 1;
						startsOfWords[m] = offsetInfo[l].getStartOffset();
						found++;
					}
				}
				for (int m = 0; m < numEnds; m++) {
					if (done[numStarts + m] == 0 && p == endsOfWords[m]) {
						done[numStarts + m] = 1;
						endsOfWords[m] = offsetInfo[l].getEndOffset();
						found++;
					}
				}

				// NOTE: we might be tempted to break here if found == total,
				// but that would foul up our calculation of highestPostLastChar and
				// lowestPosFirstChar.
			}
		}
		if (found < total) {
			if (!fillInDefaultsIfNotFound)
				throw new RuntimeException("Could not find all character offsets!");

			if (lowestPosFirstChar < 0 || highestPosLastChar < 0)
				throw new RuntimeException("Could not find default char positions!");

			for (int m = 0; m < numStarts; m++) {
				if (done[m] == 0)
					startsOfWords[m] = lowestPosFirstChar;
			}
			for (int m = 0; m < numEnds; m++) {
				if (done[numStarts + m] == 0)
					endsOfWords[m] = highestPosLastChar;
			}
		}
	}

	/**
	 * Get character positions from a list of hits.
	 *
	 * @param doc
	 *            the document from which to find character positions
	 * @param fieldName
	 *            the field from which to find character positions
	 * @param hits
	 *            the hits for which we wish to find character positions
	 * @return a list of HitSpan objects containing the character positions for the hits.
	 */
	public List<HitSpan> getCharacterOffsets(int doc, String fieldName, Hits hits) {
		int[] starts = new int[hits.size()];
		int[] ends = new int[hits.size()];
		Iterator<Hit> hitsIt = hits.iterator();
		for (int i = 0; i < starts.length; i++) {
			Hit hit = hitsIt.next(); // hits.get(i);
			starts[i] = hit.start;
			ends[i] = hit.end - 1; // end actually points to the first word not in the hit, so
									// subtract one
		}

		getCharacterOffsets(doc, fieldName, starts, ends, true);

		List<HitSpan> hitspans = new ArrayList<HitSpan>(starts.length);
		for (int i = 0; i < starts.length; i++) {
			hitspans.add(new HitSpan(starts[i], ends[i]));
		}
		return hitspans;
	}

	/**
	 * Get the contents of a field from a Lucene Document.
	 *
	 * This takes into account that some fields are stored externally in a fixed-length encoding
	 * instead of in the Lucene index.
	 *
	 * @param d
	 *            the Document
	 * @param fieldName
	 *            the name of the field
	 * @return the field content
	 */
	public String getContent(Document d, String fieldName) {
		ContentAccessor ca = contentAccessors.get(fieldName);
		String content;
		if (ca == null) {
			// No special content accessor set; assume a stored field
			content = d.get(fieldName);
		} else {
			// Content accessor set. Use it to retrieve the content.
			content = ca.getSubstringFromDocument(d, -1, -1);
		}
		return content;
	}

	/**
	 * Get the document contents (original XML).
	 *
	 * @param d
	 *            the Document
	 * @return the field content
	 */
	public String getContent(Document d) {
		return getContent(d, fieldNameContents);
	}

	/**
	 * Get the Lucene index reader we're using.
	 *
	 * @return the Lucene index reader
	 */
	public IndexReader getIndexReader() {
		return indexReader;
	}

	/**
	 * Get all the terms in the index with low edit distance from the supplied term
	 *
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
	public Set<String> getMatchingTermsFromIndex(String luceneName, Collection<String> searchTerms,
			float similarity) {
		boolean doFuzzy = true;
		if (similarity >= 0.99f) {
			// Exact match; don't use fuzzy query (slow)
			Set<String> result = new HashSet<String>();
			for (String term : searchTerms) {
				if (termOccursInIndex(new Term(luceneName, term)))
					result.add(term);
			}
			return result;
		}

		BooleanQuery q = new BooleanQuery();
		for (String s : searchTerms) {
			FuzzyQuery fq = new FuzzyQuery(new Term(luceneName, s), similarity);
			q.add(fq, Occur.SHOULD);
		}

		try {
			Query rewritten = q.rewrite(indexReader);
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
	 * Get a number of substrings from a certain field in a certain document.
	 *
	 * For larger documents, this is faster than retrieving the whole content first and then cutting
	 * substrings from that.
	 *
	 * @param d
	 *            the document
	 * @param fieldName
	 *            the field
	 * @param starts
	 *            start positions of the substring we want
	 * @param ends
	 *            end positions of the substring we want; correspond to the starts array.
	 * @return the substrings
	 */
	private String[] getSubstringsFromDocument(Document d, String fieldName, int[] starts,
			int[] ends) {
		ContentAccessor ca = contentAccessors.get(fieldName);
		String[] content;
		if (ca == null) {
			// No special content accessor set; assume a non-complex stored field
			// TODO: check with index structure?
			String luceneName = fieldName; // <- non-complex, so this works
			String fieldContent = d.get(luceneName);
			content = new String[starts.length];
			for (int i = 0; i < starts.length; i++) {
				content[i] = fieldContent.substring(starts[i], ends[i]);
			}
		} else {
			// Content accessor set. Use it to retrieve the content.
			content = ca.getSubstringsFromDocument(d, starts, ends);
		}
		return content;
	}

	/**
	 * Get a term frequency vector for a certain field in a certain document.
	 *
	 * @param doc
	 *            the document
	 * @param luceneName
	 *            the field
	 * @return the term vector
	 */
	private TermFreqVector getTermFreqVector(int doc, String luceneName) {
		try {
			// Retrieve term position vector of the contents of this document
			TermFreqVector termFreqVector = indexReader.getTermFreqVector(doc, luceneName);
			if (termFreqVector == null) {
				throw new RuntimeException("Field has no term vector!");
			}
			return termFreqVector;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Get all words between the specified start and end positions from the term vector.
	 *
	 * NOTE: this may return an array of less than the size requested, if the document ends before
	 * the requested end position.
	 *
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
	public String[] getWordsFromTermVector(int doc, String luceneName, int start, int end) {
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
	 *
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
	public List<String[]> getWordsFromTermVector(int doc, String luceneName, int[] start, int[] end) {
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
	 * Highlight field content with the specified hits.
	 *
	 * Uses &lt;hl&gt;&lt;/hl&gt; tags to highlight the content.
	 *
	 * @param docId
	 *            document to highlight a field from
	 * @param fieldName
	 *            field to highlight
	 * @param hits
	 *            the hits
	 * @return the highlighted content
	 */
	public String highlightContent(int docId, String fieldName, Hits hits) {
		// Get the field content
		Document doc = document(docId);
		String content = getContent(doc, fieldName);

		// Nothing to highlight?
		if (hits == null || hits.size() == 0)
			return content;

		// Iterate over the concordances and display
		XmlHighlighter hl = new XmlHighlighter();

		// Find the character offsets
		List<HitSpan> hitspans = getCharacterOffsets(docId, fieldName, hits);

		return hl.highlight(content, hitspans);
	}

	/**
	 * Highlight field content with the specified hits.
	 *
	 * Uses &lt;hl&gt;&lt;/hl&gt; tags to highlight the content.
	 *
	 * @param docId
	 *            document to highlight a field from
	 * @param hits
	 *            the hits
	 * @return the highlighted content
	 */
	public String highlightContent(int docId, Hits hits) {
		return highlightContent(docId, fieldNameContents, hits);
	}

	/**
	 * Determine the concordance strings for a number of concordances, given the relevant character
	 * positions.
	 *
	 * Every concordance requires four character positions: concordance start and end, and hit start
	 * and end. Visualising it ('fox' is the hit word):
	 *
	 * [A] the quick brown [B] fox [C] jumps over the [D]
	 *
	 * The startsOfWords array contains the [A] and [B] positions for each concordance. The
	 * endsOfWords array contains the [C] and [D] positions for each concordance.
	 *
	 * @param doc
	 *            the Lucene document number
	 * @param fieldName
	 *            name of the field
	 * @param startsOfWords
	 *            the array of starts of words ([A] and [B] positions)
	 * @param endsOfWords
	 *            the array of ends of words ([C] and [D] positions)
	 * @return the list of concordances
	 */
	public List<Concordance> makeFieldConcordances(int doc, String fieldName, int[] startsOfWords,
			int[] endsOfWords) {
		// Determine starts and ends
		int n = startsOfWords.length / 2;
		int[] starts = new int[n];
		int[] ends = new int[n];
		for (int i = 0, j = 0; i < startsOfWords.length; i += 2, j++) {
			starts[j] = startsOfWords[i];
			ends[j] = endsOfWords[i + 1];
		}

		// Retrieve 'em all
		Document d = document(doc);
		String[] content = getSubstringsFromDocument(d, fieldName, starts, ends);

		// Cut 'em up
		List<Concordance> rv = new ArrayList<Concordance>();
		for (int i = 0, j = 0; i < startsOfWords.length; i += 2, j++) {
			// Put the concordance in the Hit object
			int absLeft = startsOfWords[i];
			int absRight = endsOfWords[i + 1];
			int relHitLeft = startsOfWords[i + 1] - absLeft;
			int relHitRight = endsOfWords[i] - absLeft;
			String currentContent = content[j];

			// Determine context and build concordance.
			// Note that hit text may be empty for hits of length zero,
			// such as a search for open tags (which have a location but zero length,
			// like a search for a word has a length 1)
			String hitText = relHitRight < relHitLeft ? "" : currentContent.substring(relHitLeft,
					relHitRight);
			String leftContext = currentContent.substring(0, relHitLeft);
			String rightContext = currentContent.substring(relHitRight, absRight - absLeft);
			rv.add(new Concordance(new String[] { leftContext, hitText, rightContext }));
		}
		return rv;
	}

	/**
	 * Register a content accessor.
	 *
	 * This tells the Searcher how the content of different fields may be accessed. This is used for
	 * making concordances, for example. Some fields are stored in the Lucene index, while others
	 * may be stored on the file system, a database, etc.
	 *
	 * @param contentAccessor
	 */
	private void registerContentAccessor(ContentAccessor contentAccessor) {
		contentAccessors.put(contentAccessor.getFieldName(), contentAccessor);
	}

	/**
	 * Register a ContentStore as a content accessor.
	 *
	 * This tells the Searcher how the content of different fields may be accessed. This is used for
	 * making concordances, for example. Some fields are stored in the Lucene index, while others
	 * may be stored on the file system, a database, etc.
	 *
	 * A ContentStore is a filesystem-based way to access the contents.
	 *
	 * @param fieldName
	 *            the field for which this is the content accessor
	 * @param contentStore
	 *            the ContentStore object by which to access the content
	 *
	 */
	private void registerContentStore(String fieldName, ContentStore contentStore) {
		registerContentAccessor(new ContentAccessorContentStore(fieldName, contentStore));
	}

	/**
	 * Test if a term occurs in the index
	 *
	 * @param term
	 *            the term
	 * @return true iff it occurs in the index
	 */
	public boolean termOccursInIndex(Term term) {
		try {
			return indexReader.docFreq(term) > 0;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Set the collator used for sorting.
	 *
	 * The default collator is a space-correct English one.
	 *
	 * @param collator
	 *            the collator
	 */
	public void setCollator(Collator collator) {
		this.collator = collator;
	}

	/**
	 * Get the collator being used for sorting.
	 *
	 * @return the collator
	 */
	public Collator getCollator() {
		return collator;
	}

	/**
	 * Retrieves the concordance information (left, hit and right context) for a number of hits in
	 * the same document from the ContentStore.
	 *
	 * NOTE: the slowest part of this is getting the character offsets (retrieving large term
	 * vectors takes time; subsequent hits from the same document are significantly faster,
	 * presumably because of caching)
	 *
	 * @param hits
	 *            the hits in question
	 * @param fieldName
	 *            Lucene index field to make conc for
	 * @param wordsAroundHit
	 *            number of words left and right of hit to fetch
	 * @param conc
	 *            where to add the concordances
	 */
	private void makeConcordancesSingleDoc(List<Hit> hits, String fieldName, int wordsAroundHit,
			Map<Hit, Concordance> conc) {
		if (hits.size() == 0)
			return;
		int doc = hits.get(0).doc;
		int arrayLength = hits.size() * 2;
		int[] startsOfWords = new int[arrayLength];
		int[] endsOfWords = new int[arrayLength];

		determineWordPositions(doc, hits, wordsAroundHit, startsOfWords, endsOfWords);

		// Get the relevant character offsets (overwrites the startsOfWords and endsOfWords
		// arrays)
		getCharacterOffsets(doc, fieldName, startsOfWords, endsOfWords, true);

		// Make all the concordances
		List<Concordance> newConcs = makeFieldConcordances(doc, fieldName, startsOfWords,
				endsOfWords);
		for (int i = 0; i < hits.size(); i++) {
			conc.put(hits.get(i), newConcs.get(i));
		}
	}

	/**
	 * Retrieves the concordance information (left, hit and right context) for a number of hits in
	 * the same document from the ContentStore.
	 *
	 * NOTE: the slowest part of this is getting the character offsets (retrieving large term
	 * vectors takes time; subsequent hits from the same document are significantly faster,
	 * presumably because of caching)
	 *
	 * @param hits
	 *            the hits in question
	 * @param forwardIndex
	 *    Forward index for the words
	 * @param punctForwardIndex
	 *    Forward index for the punctuation
	 * @param attrForwardIndices
	 *    Forward indices for the attributes, or null if none
	 * @param fieldName
	 *            Lucene index field to make conc for
	 * @param wordsAroundHit
	 *            number of words left and right of hit to fetch
	 * @param conc
	 *            where to add the concordances
	 */
	private void makeConcordancesSingleDocForwardIndex(List<Hit> hits, ForwardIndex forwardIndex,
			ForwardIndex punctForwardIndex, Map<String, ForwardIndex> attrForwardIndices, int wordsAroundHit,
			Map<Hit, Concordance> conc) {
		if (hits.size() == 0)
			return;
		int doc = hits.get(0).doc;
		int arrayLength = hits.size() * 2;
		int[] startsOfWords = new int[arrayLength];
		int[] endsOfWords = new int[arrayLength];

		determineWordPositions(doc, hits, wordsAroundHit, startsOfWords, endsOfWords);

		// Save existing context so we can restore it afterwards
		int[][] oldContext = null;
		if (hits.size() > 0 && hits.get(0).context != null)
			oldContext = getContextFromHits(hits);

		// Get punctuation context
		int[][] punctContext = null;
		if (punctForwardIndex != null) {
			getContextWords(doc, punctForwardIndex, startsOfWords, endsOfWords, hits);
			punctContext = getContextFromHits(hits);
		}
		Terms punctTerms = punctForwardIndex == null ? null : punctForwardIndex.getTerms();

		// Get attributes context
		String[] attrName = null;
		ForwardIndex[] attrFI = null;
		Terms[] attrTerms = null;
		int[][][] attrContext = null;
		if (attrForwardIndices != null) {
			int n = attrForwardIndices.size();
			attrName = new String[n];
			attrFI = new ForwardIndex[n];
			attrTerms = new Terms[n];
			attrContext = new int[n][][];
			int i = 0;
			for (Map.Entry<String, ForwardIndex> e: attrForwardIndices.entrySet()) {
				attrName[i] = e.getKey();
				attrFI[i] = e.getValue();
				attrTerms[i] = attrFI[i].getTerms();
				getContextWords(doc, attrFI[i], startsOfWords, endsOfWords, hits);
				attrContext[i] = getContextFromHits(hits);
				i++;
			}
		}

		// Get word context
		if (forwardIndex != null)
			getContextWords(doc, forwardIndex, startsOfWords, endsOfWords, hits);
		Terms terms = forwardIndex == null ? null : forwardIndex.getTerms();

		// Make the concordances from the context
		for (int i = 0; i < hits.size(); i++) {
			Hit h = hits.get(i);
			StringBuilder[] part = new StringBuilder[3];
			for (int j = 0; j < 3; j++) {
				part[j] = new StringBuilder();
			}
			int currentPart = 0;
			StringBuilder current = part[currentPart];
			for (int j = 0; j < h.context.length; j++) {

				if (j == h.contextRightStart) {
					currentPart = 2;
					current = part[currentPart];
				}

				// Add punctuation
				// (NOTE: punctuation after match is added to right context;
				//  punctuation before match is added to left context)
				if (j > 0) {
					if (punctTerms == null) {
						// There is no punctuation forward index. Just put a space
						// between every word.
						current.append(" ");
					}
					else
						current.append(punctTerms.getFromSortPosition(punctContext[i][j]));
				}

				if (currentPart == 0 && j == h.contextHitStart) {
					currentPart = 1;
					current = part[currentPart];
				}

				// Make word tag with lemma and pos attributes
				current.append("<w");
				if (attrContext != null) {
					for (int k = 0; k < attrContext.length; k++) {
						current
						 	.append(" ")
							.append(attrName[k])
							.append("=\"")
							.append(StringUtil.escapeXmlChars(attrTerms[k].getFromSortPosition(attrContext[k][i][j])))
							.append("\"");
					}
				}
				current.append(">");

				if (terms != null)
					current.append(terms.getFromSortPosition(h.context[j]));

				// End word tag
				current.append("</w>");
			}
			/*if (part[0].length() > 0)
				part[0].append(" ");*/
			String[] concStr = new String[] {part[0].toString(), part[1].toString(), part[2].toString()};
			Concordance concordance = new Concordance(concStr);
			conc.put(h, concordance);
		}

		if (oldContext != null) {
			restoreContextInHits(hits, oldContext);
		}
	}

	/**
	 * Get the context information from the list of hits, so we can
	 * look up a different context but still have access to this one as well.
	 * @param hits the hits to save the context for
	 * @return the context
	 */
	private int[][] getContextFromHits(List<Hit> hits) {
		int[][] context = new int[hits.size()][];
		for (int i = 0; i < hits.size(); i++) {
			Hit h = hits.get(i);
			context[i] = h.context;
		}
		return context;
	}

	/**
	 * Put context information into a list of hits.
	 * @param hits the hits to restore the context for
	 * @param context the context to restore
	 */
	private void restoreContextInHits(List<Hit> hits, int[][] context) {
		for (int i = 0; i < hits.size(); i++) {
			Hit h = hits.get(i);
			h.context = context[i];
		}
	}

	/**
	 * Retrieves the context (left, hit and right) for a number of hits in the same document from
	 * the forward index.
	 *
	 * @param hits
	 *            the hits in question
	 * @param fis
	 *            the forward indices to use
	 * @param fieldPropFiidName
	 *            name of the forward index id (fiid) field
	 * @param wordsAroundHit
	 *            number of words left and right of hit to fetch
	 */
	private void makeContextSingleDoc(List<Hit> hits, List<ForwardIndex> fis,
			int wordsAroundHit) {
		if (hits.size() == 0)
			return;
		int doc = hits.get(0).doc;
		int arrayLength = hits.size() * 2;
		int[] startsOfWords = new int[arrayLength];
		int[] endsOfWords = new int[arrayLength];

		determineWordPositions(doc, hits, wordsAroundHit, startsOfWords, endsOfWords);

		getContextWords(doc, fis, startsOfWords, endsOfWords, hits);
	}

	/**
	 * Determine the word positions needed to retrieve context / snippets
	 *
	 * @param doc
	 *            the document we're looking at
	 * @param hits
	 *            the hits for which we want word positions
	 * @param wordsAroundHit
	 *            the number of words around the matches word(s) we want
	 * @param startsOfWords
	 *            (out) the starts of the contexts and the hits
	 * @param endsOfWords
	 *            (out) the ends of the hits and the contexts
	 */
	private void determineWordPositions(int doc, List<Hit> hits, int wordsAroundHit,
			int[] startsOfWords, int[] endsOfWords) {
		// Determine the first and last word of the concordance, as well as the
		// first and last word of the actual hit inside the concordance.
		int startEndArrayIndex = 0;
		for (Hit hit : hits) {
			if (hit.doc != doc)
				throw new RuntimeException(
						"makeConcordancesSingleDoc() called with hits from several docs");

			int hitStart = hit.start;
			int hitEnd = hit.end - 1;

			int start = hitStart - wordsAroundHit;
			if (start < 0)
				start = 0;
			int end = hitEnd + wordsAroundHit;

			startsOfWords[startEndArrayIndex] = start;
			startsOfWords[startEndArrayIndex + 1] = hitStart;
			endsOfWords[startEndArrayIndex] = hitEnd;
			endsOfWords[startEndArrayIndex + 1] = end;

			startEndArrayIndex += 2;
		}
	}

	private void getContextWords(int doc, ForwardIndex forwardIndex,
			int[] startsOfWords, int[] endsOfWords, List<Hit> resultsList) {
		getContextWords(doc, Arrays.asList(forwardIndex), startsOfWords, endsOfWords, resultsList);
	}

	/**
	 * Get context words from the forward index.
	 *
	 * The array layout is a little unusual. If this is a typical concordance:
	 *
	 * <code>[A] left context [B] hit text [C] right context [D]</code>
	 *
	 * the positions A-D for each of the bits of context should be in the arrays startsOfWords and
	 * endsOfWords as follows:
	 *
	 * <code>starsOfWords: A1, B1, A2, B2, ...</code> <code>endsOfWords: C1, D1, C2, D2, ...</code>
	 *
	 * @param doc
	 *            the document to get context from
	 * @param contextSources
	 *            forward indices to get context from
	 * @param startsOfWords
	 *            contains, for each bit of context requested, the starting word position of the
	 *            left context and for the hit
	 * @param endsOfWords
	 *            contains, for each bit of context requested, the ending word position of the hit
	 *            and for the left context
	 * @param resultsList
	 *            the list of results to add the context to
	 */
	private void getContextWords(int doc, List<ForwardIndex> contextSources,
			int[] startsOfWords, int[] endsOfWords, List<Hit> resultsList) {

		int n = startsOfWords.length / 2;
		int[] startsOfSnippets = new int[n];
		int[] endsOfSnippets = new int[n];
		for (int i = 0, j = 0; i < startsOfWords.length; i += 2, j++) {
			startsOfSnippets[j] = startsOfWords[i];
			endsOfSnippets[j] = endsOfWords[i + 1] + 1;
		}

		int fiNumber = 0;
		for (ForwardIndex forwardIndex: contextSources) {
			// Get all the words from the forward index
			List<int[]> words;
			if (forwardIndex != null) {
				// We have a forward index for this field. Use it.
				int fiid = forwardIndex.luceneDocIdToFiid(doc);
				// Document d = document(doc);
				// int fiid = Integer.parseInt(d.get(fiidFieldName));
				words = forwardIndex.retrievePartsSortOrder(fiid, startsOfSnippets, endsOfSnippets);
			} else {
				throw new RuntimeException("Cannot get context without a forward index");
			}

			// Build the actual concordances
			Iterator<int[]> wordsIt = words.iterator();
			Iterator<Hit> resultsListIt = resultsList.iterator();
			for (int j = 0; j < n; j++) {
				int[] theseWords = wordsIt.next();

				// Put the concordance in the Hit object
				Hit hit = resultsListIt.next(); // resultsList.get(j);
				int firstWordIndex = startsOfWords[j * 2];

				if (fiNumber == 0) {
					// Allocate context array and set hit and right start and context length
					hit.context = new int[theseWords.length * contextSources.size()];
					hit.contextHitStart = startsOfWords[j * 2 + 1] - firstWordIndex;
					hit.contextRightStart = endsOfWords[j * 2] - firstWordIndex + 1;
					hit.contextLength = theseWords.length;
				}
				// Copy the context we just retrieved into the context array
				int start = fiNumber * theseWords.length;
				System.arraycopy(theseWords, 0, hit.context, start, theseWords.length);
			}

			fiNumber++;
		}
	}

	/**
	 * Opens all the forward indices, to avoid this delay later.
	 */
	public void openForwardIndices() {
		for (String field: indexStructure.getComplexFields()) {
			ComplexFieldDesc fieldDesc = indexStructure.getComplexFieldDesc(field);
			for (String property: fieldDesc.getProperties()) {
				PropertyDesc propDesc = fieldDesc.getPropertyDesc(property);
				if (propDesc.hasForwardIndex()) {
					// This property has a forward index. Make sure it is open.
					getForwardIndex(ComplexFieldUtil.propertyField(field, property));
				}
			}
		}
	}

	/**
	 * Tries to get the ForwardIndex object for the specified fieldname.
	 *
	 * Looks for an already-opened forward index first. If none is found, and if we're in
	 * "create index" mode, may create a new forward index. Otherwise, looks for an existing forward
	 * index and opens that.
	 *
	 * @param fieldPropName
	 *            the field for which we want the forward index
	 * @return the ForwardIndex if found/created, or null otherwise
	 */
	private ForwardIndex getForwardIndex(String fieldPropName) {
		ForwardIndex forwardIndex = forwardIndices.get(fieldPropName);
		if (forwardIndex == null) {
			File dir = new File(indexLocation, "fi_" + fieldPropName);

			// Special case for old BL index with "forward" as the name of the single forward index
			// (this should be removed eventually)
			if (fieldPropName.equals(fieldNameContents) && !dir.exists()) {
				// Default forward index used to be called "forward". Look for that instead.
				File alt = new File(indexLocation, "forward");
				if (alt.exists())
					dir = alt;
			}

			if (!dir.exists()) {
				// Forward index doesn't exist
				return null;
			}
			// Open forward index
			forwardIndex = ForwardIndex.open(dir, false, collator, false);
			forwardIndex.setIdTranslateInfo(indexReader, fieldPropName); // how to translate from Lucene
																		// doc to fiid
			forwardIndices.put(fieldPropName, forwardIndex);
		}
		return forwardIndex;
	}

	/**
	 * Retrieve concordancs for a list of hits.
	 *
	 * Concordances are the hit words 'centered' with a certain number of context words around them.
	 *
	 * The concordances are placed inside the Hit objects, in the conc[] array.
	 *
	 * The size of the left and right context (in words) may be set using
	 * Searcher.setConcordanceContextSize().
	 *
	 * @param fieldName
	 *            field to use for building concordances
	 * @param hits
	 *            the hits for which to retrieve concordances
	 * @param contextSize
	 *            how many words around the hit to retrieve
	 * @return the list of concordances
	 */
	public Map<Hit, Concordance> retrieveConcordances(String fieldName, List<Hit> hits,
			int contextSize) {

		if (concordancesFromForwardIndex) {
			return retrieveConcordancesForwardIndex(fieldName, hits, contextSize);
		}

		// Group hits per document
		Map<Integer, List<Hit>> hitsPerDocument = new HashMap<Integer, List<Hit>>();
		for (Hit key : hits) {
			List<Hit> hitsInDoc = hitsPerDocument.get(key.doc);
			if (hitsInDoc == null) {
				hitsInDoc = new ArrayList<Hit>();
				hitsPerDocument.put(key.doc, hitsInDoc);
			}
			hitsInDoc.add(key);
		}
		Map<Hit, Concordance> conc = new HashMap<Hit, Concordance>();
		for (List<Hit> l : hitsPerDocument.values()) {
			makeConcordancesSingleDoc(l, fieldName, contextSize, conc);
		}
		return conc;
	}

	/**
	 * Indicate how to use the forward indices to build concordances.
	 *
	 * @param wordFI FI to use as the text content of the &lt;w/&gt; tags (default "word"; null for no text content)
	 * @param punctFI FI to use as the text content between &lt;w/&gt; tags (default "punct"; null for just a space)
	 * @param attrFI FIs to use as the attributes of the &lt;w/&gt; tags (null for all other FIs)
	 */
	public void setForwardIndexConcordanceParameters(String wordFI, String punctFI, Collection<String> attrFI) {
		concWordFI = wordFI;
		concPunctFI = punctFI;
		concAttrFI = attrFI;
	}

	/**
	 * Retrieve concordancs for a list of hits using the forward index.
	 *
	 * Concordances are the hit words 'centered' with a certain number of context words around them.
	 *
	 * The concordances are placed inside the Hit objects, in the conc[] array.
	 *
	 * The size of the left and right context (in words) may be set using
	 * Searcher.setConcordanceContextSize().
	 *
	 * @param fieldName
	 *            field to use for building concordances
	 * @param hits
	 *            the hits for which to retrieve concordances
	 * @param contextSize
	 *            how many words around the hit to retrieve
	 * @return the list of concordances
	 */
	public Map<Hit, Concordance> retrieveConcordancesForwardIndex(String fieldName, List<Hit> hits,
			int contextSize) {
		// Group hits per document
		Map<Integer, List<Hit>> hitsPerDocument = new HashMap<Integer, List<Hit>>();
		for (Hit key : hits) {
			List<Hit> hitsInDoc = hitsPerDocument.get(key.doc);
			if (hitsInDoc == null) {
				hitsInDoc = new ArrayList<Hit>();
				hitsPerDocument.put(key.doc, hitsInDoc);
			}
			hitsInDoc.add(key);
		}

		ForwardIndex forwardIndex = null;
		if (concWordFI != null)
			forwardIndex = getForwardIndex(ComplexFieldUtil.propertyField(fieldName, concWordFI));

		ForwardIndex punctForwardIndex = null;
		if (concPunctFI != null)
			punctForwardIndex = getForwardIndex(ComplexFieldUtil.propertyField(fieldName, concPunctFI));

		Map<String, ForwardIndex> attrForwardIndices = new HashMap<String, ForwardIndex>();
		if (concAttrFI == null) {
			// All other FIs are attributes
			for (String p: forwardIndices.keySet()) {
				String[] components = ComplexFieldUtil.getNameComponents(p);
				String propName = components[1];
				if (propName.equals(concWordFI) || propName.equals(concPunctFI))
					continue;
				attrForwardIndices.put(propName, getForwardIndex(p));
			}
		} else {
			// Specific list of attribute FIs
			for (String p: concAttrFI) {
				attrForwardIndices.put(p, getForwardIndex(ComplexFieldUtil.propertyField(fieldName, p)));
			}
		}

		Map<Hit, Concordance> conc = new HashMap<Hit, Concordance>();
		for (List<Hit> l : hitsPerDocument.values()) {
			makeConcordancesSingleDocForwardIndex(l, forwardIndex, punctForwardIndex, attrForwardIndices, contextSize, conc);
		}
		return conc;
	}

	/**
	 * Retrieve context for a list of hits.
	 *
	 * Context are the hit words 'centered' with a certain number of context words around them.
	 *
	 * The size of the left and right context (in words) may be set using
	 * Searcher.setConcordanceContextSize().
	 *
	 * @param fieldPropName
	 *            field to use for retrieving context
	 * @param hits
	 *            the hits for which to retrieve concordances
	 * @param contextSize
	 *            how many words around the hit to retrieve
	 */
	public void retrieveContext(String fieldPropName, List<Hit> hits, int contextSize) {
		retrieveContext(Arrays.asList(fieldPropName), hits, contextSize);
	}

	/**
	 * Retrieve context for a list of hits.
	 *
	 * Context are the hit words 'centered' with a certain number of context words around them.
	 *
	 * The size of the left and right context (in words) may be set using
	 * Searcher.setConcordanceContextSize().
	 *
	 * @param fieldProps
	 *            fields to use for retrieving context
	 * @param hits
	 *            the hits for which to retrieve concordances
	 * @param contextSize
	 *            how many words around the hit to retrieve
	 */
	public void retrieveContext(List<String> fieldProps, List<Hit> hits, int contextSize) {
		// Group hits per document
		Map<Integer, List<Hit>> hitsPerDocument = new HashMap<Integer, List<Hit>>();
		for (Hit key : hits) {
			List<Hit> hitsInDoc = hitsPerDocument.get(key.doc);
			if (hitsInDoc == null) {
				hitsInDoc = new ArrayList<Hit>();
				hitsPerDocument.put(key.doc, hitsInDoc);
			}
			hitsInDoc.add(key);
		}

		List<ForwardIndex> fis = new ArrayList<ForwardIndex>();
		for (String fieldPropName: fieldProps) {
			fis.add(getForwardIndex(fieldPropName));
		}

		for (List<Hit> l : hitsPerDocument.values()) {
			makeContextSingleDoc(l, fis, contextSize);
		}
	}

	/**
	 * Get the default context size used for building concordances
	 *
	 * @return the context size
	 */
	public int getDefaultContextSize() {
		return defaultContextSize;
	}

	/**
	 * Set the default context size to use for building concordances
	 *
	 * @param defaultContextSize
	 *            the context size
	 */
	public void setDefaultContextSize(int defaultContextSize) {
		this.defaultContextSize = defaultContextSize;
	}

	/**
	 * Factory method to create a directory content store.
	 *
	 * @param indexXmlDir
	 *            the content store directory
	 * @param create if true, create a new content store even if one exists
	 * @return the content store
	 * @deprecated renamed to openContentStore()
	 */
	@Deprecated
	public ContentStore getContentStoreDir(File indexXmlDir, boolean create) {
		return openContentStore(indexXmlDir, create);
	}

	/**
	 * Factory method to create a directory content store.
	 *
	 * @param indexXmlDir
	 *            the content store directory
	 * @param create if true, create a new content store even if one exists
	 * @return the content store
	 */
	public ContentStore openContentStore(File indexXmlDir, boolean create) {
		String type;
		if (create)
			type = "utf8zip";
		else {
			VersionFile vf = ContentStoreDirAbstract.getStoreTypeVersion(indexXmlDir);
			type = vf.getType();
		}
		if (type.equals("utf8zip"))
			return new ContentStoreDirZip(indexXmlDir, create);
		if (type.equals("utf8"))
			return new ContentStoreDirUtf8(indexXmlDir, create);
		if (type.equals("utf16"))
			return new ContentStoreDir(indexXmlDir, create);
		throw new RuntimeException("Unknown content store type " + type);
	}

	/**
	 * Factory method to create a directory content store.
	 *
	 * @param indexXmlDir
	 *            the content store directory
	 * @return the content store
	 */
	public ContentStore openContentStore(File indexXmlDir) {
		return openContentStore(indexXmlDir, false);
	}

	/**
	 * Get the Terms object for the specified field.
	 *
	 * The Terms object is part of the ForwardIndex module and provides a mapping from term id to
	 * term String, and between term id and term sort position. It is used while sorting and
	 * grouping hits (by mapping the context term ids to term sort position ids), and later used to
	 * display the group name (by mapping the sort position ids back to Strings)
	 *
	 * @param fieldPropName
	 *            the field for which we want the Terms object
	 * @return the Terms object
	 * @throws RuntimeException
	 *             if this field does not have a forward index, and hence no Terms object.
	 */
	public Terms getTerms(String fieldPropName) {
		ForwardIndex forwardIndex = getForwardIndex(fieldPropName);
		if (forwardIndex == null) {
			throw new RuntimeException("Field " + fieldPropName + " has no forward index!");
		}
		return forwardIndex.getTerms();
	}

	/**
	 * Get the Terms object for the main contents field.
	 *
	 * The Terms object is part of the ForwardIndex module and provides a mapping from term id to
	 * term String, and between term id and term sort position. It is used while sorting and
	 * grouping hits (by mapping the context term ids to term sort position ids), and later used to
	 * display the group name (by mapping the sort position ids back to Strings)
	 *
	 * @return the Terms object
	 * @throws RuntimeException
	 *             if this field does not have a forward index, and hence no Terms object.
	 */
	public Terms getTerms() {
		return getTerms(ComplexFieldUtil.mainPropertyField(indexStructure, fieldNameContents));
	}

	public String getContentsFieldMainPropName() {
		return fieldNameContents;
	}

	public boolean isDefaultSearchCaseSensitive() {
		return defaultCaseSensitive;
	}

	public boolean isDefaultSearchDiacriticsSensitive() {
		return defaultDiacriticsSensitive;
	}

	public void setDefaultSearchSensitive(boolean b) {
		defaultCaseSensitive = defaultDiacriticsSensitive = b;
	}

	public void setDefaultSearchSensitive(boolean caseSensitive, boolean diacriticsSensitive) {
		defaultCaseSensitive = caseSensitive;
		defaultDiacriticsSensitive = diacriticsSensitive;
	}

	/**
	 * Get the default initial query execution context.
	 *
	 * @param fieldName
	 *            the field to search
	 * @return the query execution context
	 */
	public QueryExecutionContext getDefaultExecutionContext(String fieldName) {
		ComplexFieldDesc complexFieldDesc = indexStructure.getComplexFieldDesc(fieldName);
		if (complexFieldDesc == null)
			throw new RuntimeException("Unknown complex field " + fieldName);
		PropertyDesc mainProperty = complexFieldDesc.getMainProperty();
		if (mainProperty == null)
			throw new RuntimeException("Main property not found for " + fieldName);
		String mainPropName = mainProperty.getName();
		return new QueryExecutionContext(this, fieldName, mainPropName,
				defaultCaseSensitive, defaultDiacriticsSensitive);
	}

	/**
	 * Get the default initial query execution context.
	 *
	 * Uses the default contents field.
	 *
	 * @return the query execution context
	 */
	public QueryExecutionContext getDefaultExecutionContext() {
		return getDefaultExecutionContext(fieldNameContents);
	}

	public String getIndexName() {
		return indexLocation.toString();
	}

	/**
	 * Retrieve a single concordance. Only use if you need a larger snippet around a single
	 * hit. If you need concordances for a set of hits, just instantiate a HitsWindow and call
	 * getConcordance() on that; it will fetch all concordances in the window in a batch, which
	 * is more efficient.
	 *
	 * @param concordanceFieldName field to use for building the concordance
	 * @param hit the hit for which we want a concordance
	 * @param contextSize the desired number of words around the hit
	 * @return the concordance
	 */
	public Concordance getConcordance(String concordanceFieldName, Hit hit, int contextSize) {
		List<Hit> oneHit = Arrays.asList(hit);
		Map<Hit, Concordance> oneConc = retrieveConcordances(concordanceFieldName, oneHit, contextSize);
		return oneConc.get(hit);
	}

	// /**
	// * Get all the terms in the index with low edit distance from the supplied term
	// * @param term search term
	// * @param similarity measure of similarity we need
	// * @return the set of terms in the index that are close to our search term
	// */
	// public Set<String> getMatchingTermsFromIndex(Term term, float similarity)
	// {
	// boolean doFuzzy = true;
	// if (similarity == 1.0f)
	// {
	// // NOTE: even when we don't want to have fuzzy suggestions, we still
	// // use a FuzzyQuery, because a TermQuery isn't checked against the index
	// // on rewrite, so we won't know if it actually occurs in the index.
	// doFuzzy = false;
	// similarity = 0.75f;
	// }
	//
	// FuzzyQuery fq = new FuzzyQuery(term, similarity);
	// //TermQuery fq = new TermQuery(term);
	// try
	// {
	// Query rewritten = fq.rewrite(indexReader);
	// WeightedTerm[] wts = QueryTermExtractor.getTerms(rewritten);
	// Set<String> terms = new HashSet<String>();
	// for (WeightedTerm wt : wts)
	// {
	// if (doFuzzy || wt.getTerm().equals(term.text()))
	// {
	// terms.add(wt.getTerm());
	// }
	// }
	// return terms;
	// }
	// catch (IOException e)
	// {
	// throw new RuntimeException(e);
	// }
	// }

}
