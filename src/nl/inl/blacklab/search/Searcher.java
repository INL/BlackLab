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
import nl.inl.blacklab.index.BLDefaultAnalyzer;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.IndexStructure.ComplexFieldDesc;
import nl.inl.blacklab.search.IndexStructure.PropertyDesc;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.lucene.SpansFiltered;
import nl.inl.blacklab.search.lucene.TextPatternTranslatorSpanQuery;
import nl.inl.util.ExUtil;
import nl.inl.util.Utilities;
import nl.inl.util.VersionFile;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.index.TermVectorOffsetInfo;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;
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
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;

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

	private static boolean AUTOMATICALLY_WARM_UP_FIS = false;

	/** The collator to use for sorting. Defaults to English collator. */
	private static Collator defaultCollator = Collator.getInstance(new Locale("en", "GB"));

	/** The collator to use for sorting. Defaults to English collator. */
	private Collator collator = defaultCollator;

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
	 *
	 * @return true iff our concordances include XML tags.
	 * @deprecated always returns true now
	 */
	@Deprecated
	public boolean concordancesIncludeXmlTags() {
		return true;
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

	/** If true, we want to add/delete documents. If false, we're just searching. */
	private boolean indexMode = false;

	/** If true, we've just created a new index. Only valid in indexMode */
	private boolean createdNewIndex = false;

	/** The index writer. Only valid in indexMode. */
	private IndexWriter indexWriter = null;

	/**
	 * Open an index for writing ("index mode": adding/deleting documents).
	 *
	 * Note that in index mode, searching operations may not take the latest
	 * changes into account. It is wisest to only use index mode for indexing,
	 * then close the Searcher and create a regular one for searching.
	 *
	 * @param indexDir the index directory
	 * @param createNewIndex if true, create a new index even if one existed there
	 * @return the searcher in index mode
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	public static Searcher openForWriting(File indexDir, boolean createNewIndex)
			throws CorruptIndexException, IOException {
		return new Searcher(indexDir, true, createNewIndex);
	}

	private Searcher(File indexDir, boolean indexMode, boolean createNewIndex)
			throws CorruptIndexException, IOException {
		this.indexMode = indexMode;

		if (!indexMode && createNewIndex)
			throw new RuntimeException("Cannot create new index, not in index mode");

		createdNewIndex = createNewIndex;
		if (!createNewIndex) {
			if (!indexMode || VersionFile.exists(indexDir)) {
				if (!VersionFile.isTypeVersion(indexDir, "blacklab", "1")
						&& !VersionFile.isTypeVersion(indexDir, "blacklab", "2"))
					throw new RuntimeException("BlackLab index has wrong type or version! "
							+ VersionFile.report(indexDir));
			}
		}

		logger.debug("Constructing Searcher...");

		if (indexMode) {
			indexWriter = openIndexWriter(indexDir, createNewIndex);
			indexReader = IndexReader.open(indexWriter, false);
		} else {
			// Open Lucene index
			indexReader = IndexReader.open(FSDirectory.open(indexDir));
		}
		this.indexLocation = indexDir;

		// Determine the index structure
		indexStructure = new IndexStructure(indexReader);

		// Detect and open the ContentStore for the contents field
		if (!createNewIndex) {
			ComplexFieldDesc mainContentsField = indexStructure.getMainContentsField();
			if (mainContentsField == null) {
				if (!indexMode)
					throw new RuntimeException("Could not detect main contents field");
			} else {
				this.fieldNameContents = mainContentsField.getName();

				// See if we have a punctuation forward index. If we do,
				// default to creating concordances using that.
				if (mainContentsField.hasPunctuation()) {
					concordancesFromForwardIndex = true;
				}
			}

			// Register content stores
			for (String cfn: indexStructure.getComplexFields()) {
				if (indexStructure.getComplexFieldDesc(cfn).hasContentStore()) {
					File dir = new File(indexDir, "cs_" + cfn);
					if (!dir.exists()) {
						dir = new File(indexDir, "xml"); // OLD, should eventually be removed
					}
					if (dir.exists()) {
						registerContentStore(fieldNameContents, openContentStore(dir));
					}
				}
			}
		}

		indexSearcher = new IndexSearcher(indexReader);

		// Make sure large wildcard/regex expansions succeed
		BooleanQuery.setMaxClauseCount(100000);

		// Open the forward indices
		if (!createNewIndex)
			openForwardIndices();
		logger.debug("Done.");
	}

	/**
	 * Construct a Searcher object, the main search interface on a BlackLab index.
	 *
	 * @param indexDir
	 *            the index directory
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	public Searcher(File indexDir) throws CorruptIndexException, IOException {
		this(indexDir, false, false);
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
	void getCharacterOffsets(int doc, String fieldName, int[] startsOfWords,
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
	private List<HitSpan> getCharacterOffsets(int doc, String fieldName, Hits hits) {
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
	 * Get the content store for a field name
	 * @param fieldName the field name
	 * @return the content store, or null if there is no content store for this field
	 */
	public ContentStore getContentStore(String fieldName) {
		ContentAccessor ca = contentAccessors.get(fieldName);
		if (indexMode && ca == null) {
			// Index mode. Create new content store.
			ContentStore contentStore = new ContentStoreDirZip(new File(indexLocation, "cs_" + fieldName),
					createdNewIndex);
			registerContentStore(fieldName, contentStore);
			return contentStore;
		}
		if (ca instanceof ContentAccessorContentStore) {
			return ((ContentAccessorContentStore) ca).getContentStore();
		}
		return null;
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
	 * The default collator is for English.
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
	 * Opens all the forward indices, to avoid this delay later.
	 *
	 * NOTE: used to be public; now private because it's done automatically when
	 * constructing the Searcher.
	 */
	private void openForwardIndices() {
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

		if (!indexMode && AUTOMATICALLY_WARM_UP_FIS) {
			warmUpForwardIndices();
		}
	}

	/**
	 * "Warm up" the forward indices by performing a large number of reads on them,
	 * getting them into disk cache.
	 */
	public void warmUpForwardIndices() {
		for (Map.Entry<String, ForwardIndex> e: forwardIndices.entrySet()) {
			logger.debug("Warming up " + e.getKey() + "...");
			e.getValue().warmUp();
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
	public ForwardIndex getForwardIndex(String fieldPropName) {
		ForwardIndex forwardIndex = forwardIndices.get(fieldPropName);
		if (forwardIndex == null) {
			File dir = new File(indexLocation, "fi_" + fieldPropName);

			// Special case for old BL index with "forward" as the name of the single forward index
			// (this should be removed eventually)
			if (!createdNewIndex && fieldPropName.equals(fieldNameContents) && !dir.exists()) {
				// Default forward index used to be called "forward". Look for that instead.
				File alt = new File(indexLocation, "forward");
				if (alt.exists())
					dir = alt;
			}

			if (!createdNewIndex && !dir.exists()) {
				// Forward index doesn't exist
				return null;
			}
			// Open forward index
			forwardIndex = ForwardIndex.open(dir, indexMode, collator, createdNewIndex);
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
	 * The size of the left and right context (in words) may be set using
	 * Searcher.setConcordanceContextSize().
	 *
	 * @param hits
	 *            the hits for which to retrieve concordances
	 * @param contextSize
	 *            how many words around the hit to retrieve
	 * @param fieldName
	 *            field to use for building concordances
	 *
	 * @return the list of concordances
	 */
	Map<Hit, Concordance> retrieveConcordances(Hits hits, int contextSize,
			String fieldName) {

		// Group hits per document
		Map<Integer, List<Hit>> hitsPerDocument = new HashMap<Integer, List<Hit>>();
		for (Hit key: hits) {
			List<Hit> hitsInDoc = hitsPerDocument.get(key.doc);
			if (hitsInDoc == null) {
				hitsInDoc = new ArrayList<Hit>();
				hitsPerDocument.put(key.doc, hitsInDoc);
			}
			hitsInDoc.add(key);
		}

		if (concordancesFromForwardIndex) {
			// Yes, make 'em from the forward index (faster)
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

			Map<Hit, Concordance> conc1 = new HashMap<Hit, Concordance>();
			for (List<Hit> l: hitsPerDocument.values()) {
				Hits hitsInThisDoc = new Hits(this, l);
				hitsInThisDoc.makeConcordancesSingleDocForwardIndex(forwardIndex, punctForwardIndex, attrForwardIndices, contextSize, conc1);
			}
			return conc1;
		}

		// Not from forward index; make 'em from the content store (slower)
		Map<Hit, Concordance> conc = new HashMap<Hit, Concordance>();
		for (List<Hit> l: hitsPerDocument.values()) {
			Hits hitsInThisDoc = new Hits(this, l);
			hitsInThisDoc.makeConcordancesSingleDoc(fieldName, contextSize, conc);
		}
		return conc;
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
	List<Concordance> makeFieldConcordances(int doc, String fieldName, int[] startsOfWords,
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

	public static IndexWriter openIndexWriter(File indexDir, boolean create) throws IOException,
			CorruptIndexException, LockObtainFailedException {
		if (!indexDir.exists() && create) {
			indexDir.mkdir();
		}
		Analyzer analyzer = new BLDefaultAnalyzer(); // (Analyzer)analyzerClass.newInstance();
		Directory indexLuceneDir = FSDirectory.open(indexDir);
		IndexWriterConfig config = Utilities.getIndexWriterConfig(analyzer, create);
		IndexWriter writer = new IndexWriter(indexLuceneDir, config);

		if (create)
			VersionFile.write(indexDir, "blacklab", "2");
		else {
			if (!VersionFile.isTypeVersion(indexDir, "blacklab", "1")
					&& !VersionFile.isTypeVersion(indexDir, "blacklab", "2")) {
				throw new RuntimeException("BlackLab index has wrong type or version! "
						+ VersionFile.report(indexDir));
			}
		}

		return writer;
	}

	public static Collator getDefaultCollator() {
		return defaultCollator;
	}

	public static void setDefaultCollator(Collator defaultCollator) {
		Searcher.defaultCollator = defaultCollator;
	}

	public IndexWriter getWriter() {
		return indexWriter;
	}

	public File getIndexDirectory() {
		return indexLocation;
	}

	/** Deletes documents matching a query from the BlackLab index.
	 *
	 * This deletes the documents from the Lucene index, the forward indices and the content store(s).
	 * @param q the query
	 */
	public void delete(Query q) {
		if (!indexMode)
			throw new RuntimeException("Cannot delete documents, not in index mode");
		try {
			// Open a fresh reader to execute the query
			IndexReader reader = IndexReader.open(indexWriter, false);
			try {
				// Execute the query, iterate over the docs and delete from FI and CS.
				IndexSearcher s = new IndexSearcher(reader);
				try {
					Weight w = s.createNormalizedWeight(q);
					Scorer sc = w.scorer(reader, true, false);
					// Iterate over matching docs
					while (true) {
						int docId;
						try {
							docId = sc.nextDoc();
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						if (docId == DocIdSetIterator.NO_MORE_DOCS)
							break;
						Document d = reader.document(docId);

						// Delete this document in all forward indices
						for (Map.Entry<String, ForwardIndex> e: forwardIndices.entrySet()) {
							String fieldName = e.getKey();
							ForwardIndex fi = e.getValue();
							int fiid = Integer.parseInt(d.get(ComplexFieldUtil
									.forwardIndexIdField(fieldName)));
							fi.deleteDocument(fiid);
						}

						// Delete this document in all content stores
						for (Map.Entry<String, ContentAccessor> e: contentAccessors.entrySet()) {
							String fieldName = e.getKey();
							ContentAccessor ca = e.getValue();
							if (!(ca instanceof ContentAccessorContentStore))
								continue; // can only delete from content store
							ContentStore cs = ((ContentAccessorContentStore)ca).getContentStore();
							int cid = Integer.parseInt(d.get(ComplexFieldUtil
									.contentIdField((fieldName))));
							cs.delete(cid);
						}
					}
				} finally {
					s.close();
				}
			} finally {
				reader.close();
			}

			// Finally, delete the documents from the Lucene index
			indexWriter.deleteDocuments(q);

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}


}
