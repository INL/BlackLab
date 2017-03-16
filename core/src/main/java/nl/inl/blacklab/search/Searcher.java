package nl.inl.blacklab.search;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.store.LockObtainFailedException;

import nl.inl.blacklab.analysis.BLDutchAnalyzer;
import nl.inl.blacklab.analysis.BLNonTokenizingAnalyzer;
import nl.inl.blacklab.analysis.BLStandardAnalyzer;
import nl.inl.blacklab.analysis.BLWhitespaceAnalyzer;
import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.externalstorage.ContentStoresManager;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.highlight.XmlHighlighter;
import nl.inl.blacklab.highlight.XmlHighlighter.HitCharSpan;
import nl.inl.blacklab.highlight.XmlHighlighter.UnbalancedTagsStrategy;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.util.VersionFile;

public abstract class Searcher {

	protected static final Logger logger = LogManager.getLogger(Searcher.class);

	/** When setting how many hits to retrieve/count, this means "no limit". */
	public final static int UNLIMITED_HITS = -1;

	public static final int DEFAULT_MAX_RETRIEVE = 1000000;

	public static final int DEFAULT_MAX_COUNT = Searcher.UNLIMITED_HITS;

	/** Complex field name for default contents field */
	public static final String DEFAULT_CONTENTS_FIELD_NAME = "contents";

	public static final ConcordanceType DEFAULT_CONC_TYPE = ConcordanceType.CONTENT_STORE;

	public static final String DEFAULT_CONC_WORD_PROP = ComplexFieldUtil.WORD_PROP_NAME;

	public static final String DEFAULT_CONC_PUNCT_PROP = ComplexFieldUtil.PUNCTUATION_PROP_NAME;

	public static final Collection<String> DEFAULT_CONC_ATTR_PROP = null;

	public static final int DEFAULT_CONTEXT_SIZE = 5;

	/** The collator to use for sorting. Defaults to English collator. */
	protected static Collator defaultCollator = Collator.getInstance(new Locale("en", "GB"));

	/** Analyzer based on WhitespaceTokenizer */
	final protected static Analyzer whitespaceAnalyzer = new BLWhitespaceAnalyzer();

	/** Analyzer for Dutch and other Latin script languages */
	final protected static Analyzer defaultAnalyzer = new BLDutchAnalyzer();

	/** Analyzer based on StandardTokenizer */
	final protected static Analyzer standardAnalyzer = new BLStandardAnalyzer();

	/** Analyzer that doesn't tokenize */
	final protected static Analyzer nonTokenizingAnalyzer = new BLNonTokenizingAnalyzer();

	final protected static Map<IndexReader, Searcher> searcherFromIndexReader = new IdentityHashMap<>();

	public static Searcher fromIndexReader(IndexReader reader) {
		return searcherFromIndexReader.get(reader);
	}

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
	 * @throws IOException
	 */
	public static Searcher openForWriting(File indexDir, boolean createNewIndex)
			throws IOException {
		return new SearcherImpl(indexDir, true, createNewIndex, (File)null);
	}

	/**
	 * Open an index for writing ("index mode": adding/deleting documents).
	 *
	 * Note that in index mode, searching operations may not take the latest
	 * changes into account. It is wisest to only use index mode for indexing,
	 * then close the Searcher and create a regular one for searching.
	 *
	 * @param indexDir the index directory
	 * @param createNewIndex if true, create a new index even if one existed there
	 * @param indexTemplateFile JSON template to use for index structure / metadata
	 * @return the searcher in index mode
	 * @throws IOException
	 */
	public static Searcher openForWriting(File indexDir, boolean createNewIndex,
			File indexTemplateFile) throws IOException {
		return new SearcherImpl(indexDir, true, createNewIndex, indexTemplateFile);
	}

	/**
	 * Create an empty index.
	 *
	 * @param indexDir where to create the index
	 * @return a Searcher for the new index, in index mode
	 * @throws IOException
	 */
	public static Searcher createIndex(File indexDir) throws IOException {
		return createIndex(indexDir, null, null, false);
	}

	/**
	 * Create an empty index.
	 *
	 * @param indexDir where to create the index
	 * @param displayName the display name for the new index, or null to
	 *   assign one automatically (based on the directory name)
	 * @return a Searcher for the new index, in index mode
	 * @throws IOException
	 */
	public static Searcher createIndex(File indexDir, String displayName) throws IOException {
		return createIndex(indexDir, displayName, null, false);
	}

	/**
	 * Create an empty index.
	 *
	 * @param indexDir where to create the index
	 * @param displayName the display name for the new index, or null to
	 *   assign one automatically (based on the directory name)
	 * @param documentFormat a format identifier to store as the document format,
	 *   or null for none. See the DocumentFormats class.
	 * @param contentViewable is viewing of the document contents allowed?
	 * @return a Searcher for the new index, in index mode
	 * @throws IOException
	 */
	public static Searcher createIndex(File indexDir, String displayName, String documentFormat, boolean contentViewable) throws IOException {
		Searcher rv = openForWriting(indexDir, true);
		if (displayName != null && displayName.length() > 0) {
			rv.getIndexStructure().setDisplayName(displayName);
		}
		if (documentFormat != null) {
			rv.getIndexStructure().setDocumentFormat(documentFormat);
		}
		rv.getIndexStructure().setContentViewable(contentViewable);
		rv.getIndexStructure().writeMetadata();
		return rv;
	}

	/**
	 * Open an index for reading ("search mode").
	 *
	 * @param indexDir the index directory
	 * @return the searcher
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	public static Searcher open(File indexDir) throws CorruptIndexException, IOException {
		return new SearcherImpl(indexDir, false, false, (File)null);
	}

	/**
	 * Does the specified directory contain a BlackLab index?
	 * @param indexDir the directory
	 * @return true if it's a BlackLab index, false if not.
	 */
	public static boolean isIndex(File indexDir) {
		try {
			if (VersionFile.exists(indexDir)) {
				VersionFile vf = VersionFile.read(indexDir);
				String version = vf.getVersion();
				if (vf.getType().equals("blacklab") && (version.equals("1") || version.equals("2")))
					return true;
			}
			return false;
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Cut a few words from a string.
	 *
	 * Note, this just splits on whitespace and glues words
	 * back with space. Might not work very well in all cases,
	 * but it's not likely to be used anyway (we generally don't
	 * cut a few words from a metadata field).
	 *
	 * @param content the string to cut from
	 * @param startAtWord first word to include
	 * @param endAtWord first word not to include
	 * @return the cut string
	 */
	protected static String getWordsFromString(String content, int startAtWord,
			int endAtWord) {
		if (startAtWord == -1 && endAtWord == -1)
			return content;
		// We want specific words from the field; quick-n-dirty way to do this
		// (will probably never be used, but let's try to be generic)
		String[] words = content.split("\\s+");
		if (startAtWord == -1)
			startAtWord = 0;
		if (endAtWord == -1)
			endAtWord = words.length;
		StringBuilder b = new StringBuilder();
		for (int i = startAtWord; i < endAtWord; i++) {
			if (b.length() > 0)
				b.append(" ");
			b.append(words[i]);
		}
		return b.toString();
	}

	public static Collator getDefaultCollator() {
		return defaultCollator;
	}

	public static void setDefaultCollator(Collator defaultCollator) {
		Searcher.defaultCollator = defaultCollator;
	}

	/**
	 * Return a timestamp for when BlackLab was built.
	 *
	 * @return build timestamp (format: yyyy-MM-dd HH:mm:ss), or UNKNOWN if
	 *   the timestamp could not be found for some reason (i.e. not running from a
	 *   JAR, or key not found in manifest).
	 */
	public static String getBlackLabBuildTime() {
		return getValueFromManifest("Build-Time", "UNKNOWN");
	}

	/**
	 * Return the BlackLab version.
	 *
	 * @return BlackLab version, or UNKNOWN if the version could not be found
	 *   for some reason (i.e. not running from a JAR, or key not found in manifest).
	 */
	public static String getBlackLabVersion() {
		return getValueFromManifest("Implementation-Version", "UNKNOWN");
	}

	/**
	 * Get a value from the manifest file, if available.
	 *
	 * @param key key to get the value for, e.g. "Build-Time".
	 * @param defaultValue value to return if no manifest found or key not found
	 * @return value from the manifest, or the default value if not found
	 */
	static String getValueFromManifest(String key, String defaultValue) {
		try {
			URL res = Searcher.class.getResource(Searcher.class.getSimpleName() + ".class");
			URLConnection conn = res.openConnection();
			if (!(conn instanceof JarURLConnection)) {
				// Not running from a JAR, no manifest to read
				return defaultValue;
			}
			JarURLConnection jarConn = (JarURLConnection) res.openConnection();
			Manifest mf = jarConn.getManifest();
			String value = null;
			if (mf != null) {
				Attributes atts = mf.getMainAttributes();
				if (atts != null) {
					value = atts.getValue(key);
				}
			}
			return value == null ? defaultValue : value;
		} catch (IOException e) {
			throw new RuntimeException("Could not read '" + key + "' from manifest", e);
		}
	}

	/**
	 * Instantiate analyzer based on an analyzer alias.
	 *
	 * @param analyzerName type of analyzer (default|whitespace|standard|nontokenizing)
	 * @return the analyzer, or null if the name wasn't recognized
	 */
	static Analyzer getAnalyzerInstance(String analyzerName) {
		analyzerName = analyzerName.toLowerCase();
		if (analyzerName.equals("whitespace")) {
			return whitespaceAnalyzer;
		} else if (analyzerName.equals("default")) {
			return defaultAnalyzer;
		} else if (analyzerName.equals("standard")) {
			return standardAnalyzer;
		} else if (analyzerName.matches("(non|un)tokeniz(ing|ed)")) {
			return nonTokenizingAnalyzer;
		}
		return null;
	}

	//-------------------------------------------------------------------------

	/** The collator to use for sorting. Defaults to English collator. */
	private Collator collator = Searcher.defaultCollator;

	/** Analyzer used for indexing our metadata fields */
	protected Analyzer analyzer = new BLStandardAnalyzer();

	/** Structure of our index */
	protected IndexStructure indexStructure;

	protected ContentStoresManager contentStores = new ContentStoresManager();

	/**
	 * ForwardIndices allow us to quickly find what token occurs at a specific position. This speeds
	 * up grouping and sorting. There may be several indices on a complex field, e.g.: word form,
	 * lemma, part of speech.
	 *
	 * Indexed by property name.
	 */
	protected Map<String, ForwardIndex> forwardIndices = new HashMap<>();

	protected HitsSettings hitsSettings;

	/**
	 * The default settings for all new Hits objects.
	 *
	 * You may change these settings; this will affect all new Hits objects.
	 *
	 * @return settings object
	 */
	public HitsSettings hitsSettings() {
		return hitsSettings;
	}

	public Searcher() {
		hitsSettings = new HitsSettings();
	}

//	/**
//	 * Stop retrieving hits after this number.
//	 * (HitsSettings.UNLIMITED = don't stop retrieving)
//	 */
//	protected int defaultMaxHitsToRetrieve = DEFAULT_MAX_RETRIEVE;
//
//	/**
//	 * Stop counting hits after this number.
//	 * (HitsSettings.UNLIMITED = don't stop counting)
//	 */
//	protected int defaultMaxHitsToCount = DEFAULT_MAX_COUNT;
//
//	/** Do we want to retrieve concordances from the forward index instead of from the
//	 *  content store? Generating them from the forward index is more
//	 *  efficient.
//	 *
//	 *  This is set to FORWARD_INDEX for all modern indices.
//	 *  (to be precise, it's set to true iff a punctuation forward index is present)
//	 *
//	 *  This setting controls the default. You don't have to set this to CONTENT_STORE if
//	 *  you *sometimes* want concordances from the content store; you can specifically
//	 *  request those when you need them.
//	 */
//	private ConcordanceType defaultConcsType = DEFAULT_CONC_TYPE;
//
	/**
	 * Name of the main contents field (used as default parameter value for many methods)
	 */
	protected String mainContentsFieldName = DEFAULT_CONTENTS_FIELD_NAME;
//
//	/** Forward index to use as text context of &lt;w/&gt; tags in concordances (words; null = no text content) */
//	private String concWordFI = DEFAULT_CONC_WORD_PROP;
//
//	/** Forward index to use as text context between &lt;w/&gt; tags in concordances (punctuation+whitespace; null = just a space) */
//	private String concPunctFI = DEFAULT_CONC_PUNCT_PROP;
//
//	/** Forward indices to use as attributes of &lt;w/&gt; tags in concordances (null = the rest) */
//	private Collection<String> concAttrFI = DEFAULT_CONC_ATTR_PROP; // all other FIs are attributes
//
//	/** Default number of words around a hit */
//	protected int defaultContextSize = DEFAULT_CONTEXT_SIZE;

	/** Should we default to case-sensitive searching? [false] */
	protected boolean defaultCaseSensitive = false;

	/** Should we default to diacritics-sensitive searching? [false] */
	protected boolean defaultDiacriticsSensitive = false;

	/** How we fix well-formedness for snippets of XML: by adding or removing unbalanced tags */
	private UnbalancedTagsStrategy defaultUnbalancedTagsStrategy = UnbalancedTagsStrategy.ADD_TAG;

	/** If true, we want to add/delete documents. If false, we're just searching. */
	protected boolean indexMode = false;

	/** @return the default maximum number of hits to retrieve.
	 * @deprecated use hitsSettings().maxHitsToRetrieve()
	 */
	@Deprecated
	public int getDefaultMaxHitsToRetrieve() {
		return hitsSettings().maxHitsToRetrieve();
	}

	/** Set the default maximum number of hits to retrieve
	 * @param n the number of hits, or HitsSettings.UNLIMITED for no limit
	 * @deprecated use hitsSettings().setMaxHitsToRetrieve()
	 */
	@Deprecated
	public void setDefaultMaxHitsToRetrieve(int n) {
		hitsSettings().setMaxHitsToRetrieve(n);
	}

	/** @return the default maximum number of hits to count.
	 * @deprecated use hitsSettings().maxHitsToCount()
	 */
	@Deprecated
	public int getDefaultMaxHitsToCount() {
		return hitsSettings().maxHitsToCount();
	}

	/** Set the default maximum number of hits to count
	 * @param n the number of hits, or HitsSettings.UNLIMITED for no limit
	 * @deprecated use hitsSettings().setMaxHitsToCount()
	 */
	@Deprecated
	public void setDefaultMaxHitsToCount(int n) {
		hitsSettings().setMaxHitsToCount(n);
	}

	/**
	 * How do we fix well-formedness for snippets of XML?
	 * @return the setting: either adding or removing unbalanced tags
	 */
	public UnbalancedTagsStrategy getDefaultUnbalancedTagsStrategy() {
		return defaultUnbalancedTagsStrategy;
	}

	/**
	 * Set how to fix well-formedness for snippets of XML.
	 * @param strategy the setting: either adding or removing unbalanced tags
	 */
	public void setDefaultUnbalancedTagsStrategy(UnbalancedTagsStrategy strategy) {
		this.defaultUnbalancedTagsStrategy = strategy;
	}

	/**
	 * @return the default concordance type
	 * @deprecated use hitsSettings().concordanceType()
	 */
	@Deprecated
	public ConcordanceType getDefaultConcordanceType() {
		return hitsSettings().concordanceType();
	}

	/**
	 * @param type the default concordance type
	 * @deprecated use hitsSettings().setConcordanceType()
	 */
	@Deprecated
	public void setDefaultConcordanceType(ConcordanceType type) {
		hitsSettings().setConcordanceType(type);
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
	 * Are we making concordances using the forward index (true) or using
	 * the content store (false)? Forward index is more efficient but returns
	 * concordances that don't include XML tags.
	 *
	 * @return true iff we use the forward index for making concordances.
	 * @deprecated use hitsSettings().concordanceType()
	 */
	@Deprecated
	public boolean getMakeConcordancesFromForwardIndex() {
		return getDefaultConcordanceType() == ConcordanceType.FORWARD_INDEX;
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
	 * @deprecated use hitsSettings().setConcordanceType()
	 */
	@Deprecated
	public void setMakeConcordancesFromForwardIndex(boolean concordancesFromForwardIndex) {
		setDefaultConcordanceType(concordancesFromForwardIndex ? ConcordanceType.FORWARD_INDEX : ConcordanceType.CONTENT_STORE);
	}

	/**
	 * Is this a newly created, empty index?
	 * @return true if it is, false if not
	 */
	public abstract boolean isEmpty();

	/**
	 * Call this to roll back any changes made to the index this session.
	 * Calling close() will automatically commit any changes. If you call this
	 * method, then call close(), no changes will be committed.
	 */
	public abstract void rollback();

	/**
	 * Finalize the Searcher object. This closes the IndexSearcher and (depending on the constructor
	 * used) may also close the index reader.
	 */
	public void close() {
		contentStores.close();

		// Close the forward indices
		for (ForwardIndex fi: forwardIndices.values()) {
			fi.close();
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
	 * NOTE: you must check if the document isn't deleted using Search.isDeleted()
	 * first! Lucene 4.0+ allows you to retrieve deleted documents, making you
	 * responsible for checking whether documents are deleted or not.
	 * (This doesn't apply to search results; searches should never produce deleted
	 *  documents. It does apply when you're e.g. iterating over all documents in the index)
	 *
	 * @param doc
	 *            the document id
	 * @return the Lucene Document
	 * @throws RuntimeException if the document doesn't exist (use maxDoc() and isDeleted() to check first!)
	 */
	public abstract Document document(int doc);

	/**
	 * Get a set of all (non-deleted) Lucene document ids.
	 * @return set of ids
	 */
	public abstract Set<Integer> docIdSet();

	/** A task to perform on a Lucene document. */
	public interface LuceneDocTask {
		void perform(Document doc);
	}

	/**
	 * Perform a task on each (non-deleted) Lucene Document.
	 * @param task task to perform
	 */
	public void forEachDocument(LuceneDocTask task) {
		for (Integer docId: docIdSet()) {
			task.perform(document(docId));
		}
	}

	/**
	 * Checks if a document has been deleted from the index
	 * @param doc the document id
	 * @return true iff it has been deleted
	 */
	public abstract boolean isDeleted(int doc);

	/**
	 * Returns one more than the highest document id
	 * @return one more than the highest document id
	 */
	public abstract int maxDoc();

	@Deprecated
	public BLSpanQuery filterDocuments(SpanQuery query, org.apache.lucene.search.Filter filter) {
		if (!(query instanceof BLSpanQuery))
			throw new IllegalArgumentException("Supplied query must be a BLSpanQuery!");
		return new SpanQueryFiltered((BLSpanQuery) query, filter);
	}

	@Deprecated
	public BLSpanQuery createSpanQuery(TextPattern pattern, String fieldName, org.apache.lucene.search.Filter filter) {
		if (filter == null || filter instanceof org.apache.lucene.search.QueryWrapperFilter) {
			Query filterQuery = filter == null ? null : ((org.apache.lucene.search.QueryWrapperFilter) filter).getQuery();
			return createSpanQuery(pattern, fieldName, filterQuery);
		}
		throw new UnsupportedOperationException("Filter must be a QueryWrapperFilter!");
	}

	@Deprecated
	public BLSpanQuery createSpanQuery(TextPattern pattern, org.apache.lucene.search.Filter filter) {
		return createSpanQuery(pattern, getMainContentsFieldName(), filter);
	}

	/**
	 * @deprecated use version that takes a filter, and pass null for no filter
	 */
	@SuppressWarnings("javadoc")
	@Deprecated
	public BLSpanQuery createSpanQuery(TextPattern pattern, String fieldName) {
		return createSpanQuery(pattern, fieldName, (Query)null);
	}

	/**
	 * @deprecated use version that takes a filter, and pass null for no filter
	 */
	@SuppressWarnings("javadoc")
	@Deprecated
	public BLSpanQuery createSpanQuery(TextPattern pattern) {
		return createSpanQuery(pattern, getMainContentsFieldName(), (Query)null);
	}

	public BLSpanQuery createSpanQuery(TextPattern pattern, String fieldName, Query filter) {
		// Convert to SpanQuery
		//pattern = pattern.rewrite();
		BLSpanQuery spanQuery = pattern.translate(getDefaultExecutionContext(fieldName));
		if (filter != null)
			spanQuery = new SpanQueryFiltered(spanQuery, filter);
		return spanQuery;
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
		if (!(query instanceof BLSpanQuery))
			throw new IllegalArgumentException("Supplied query must be a BLSpanQuery!");
		Hits hits = Hits.fromSpanQuery(this, query);
		hits.settings.setConcordanceField(fieldNameConc);
		return hits;
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
	public Hits find(BLSpanQuery query) throws BooleanQuery.TooManyClauses {
		return Hits.fromSpanQuery(this, query);
	}

	/**
	 * Find hits for a pattern in a field.
	 *
	 * @param pattern
	 *            the pattern to find
	 * @param fieldName
	 *            field to find pattern in
	 * @param filter
	 *            determines which documents to search
	 *
	 * @return the hits found
	 * @throws BooleanQuery.TooManyClauses
	 *             if a wildcard or regular expression term is overly broad
	 */
	public Hits find(TextPattern pattern, String fieldName, Query filter)
			throws BooleanQuery.TooManyClauses {
		Hits hits = Hits.fromSpanQuery(this, createSpanQuery(pattern, fieldName, filter));
		hits.settings.setConcordanceField(fieldName);
		return hits;
	}

	public Hits find(TextPattern pattern, Query filter) {
		return find(pattern, getMainContentsFieldName(), filter);
	}

	/**
	 * @deprecated use version that takes a Query as a filter
	 */
	@SuppressWarnings("javadoc")
	@Deprecated
	public Hits find(TextPattern pattern, String fieldName, org.apache.lucene.search.Filter filter) {
		if (filter == null || filter instanceof org.apache.lucene.search.QueryWrapperFilter) {
			Query filterQuery = filter == null ? null : ((org.apache.lucene.search.QueryWrapperFilter) filter).getQuery();
			return find(createSpanQuery(pattern, fieldName, filterQuery), fieldName);
		}
		throw new UnsupportedOperationException("Filter must be a QueryWrapperFilter!");
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
	 * @deprecated use version that takes a Query as a filter
	 */
	@Deprecated
	public Hits find(TextPattern pattern, org.apache.lucene.search.Filter filter) throws BooleanQuery.TooManyClauses {
		return find(pattern, getMainContentsFieldName(), filter);
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
		return find(pattern, getMainContentsFieldName(), null);
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
	public abstract void getCharacterOffsets(int doc, String fieldName, int[] startsOfWords, int[] endsOfWords,
			boolean fillInDefaultsIfNotFound);

	public DocContentsFromForwardIndex getContentFromForwardIndex(int docId, String fieldName, int startAtWord, int endAtWord) {
		Hit hit = new Hit(docId, startAtWord, endAtWord);
		Hits hits = Hits.fromList(this, Arrays.asList(hit));
		hits.settings.setConcordanceField(fieldName);
		Kwic kwic = hits.getKwic(hit, 0);
		return kwic.getDocContents();
	}

	/**
	 * Get part of the contents of a field from a Lucene Document.
	 *
	 * This takes into account that some fields are stored externally in content stores
	 * instead of in the Lucene index.
	 *
	 * @param docId
	 *            the Lucene Document id
	 * @param fieldName
	 *            the name of the field
	 * @param startAtChar where to start getting the content (-1 for start of document, 0 for first char)
	 * @param endAtChar where to end getting the content (-1 for end of document)
	 * @return the field content
	 */
	public String getContentByCharPos(int docId, String fieldName, int startAtChar, int endAtChar) {
		Document d = document(docId);
		if (!contentStores.exists(fieldName)) {
			// No special content accessor set; assume a stored field
			return d.get(fieldName).substring(startAtChar, endAtChar);
		}
		return contentStores.getSubstrings(fieldName, d, new int[] { startAtChar }, new int[] { endAtChar })[0];
	}

	/**
	 * Get part of the contents of a field from a Lucene Document.
	 *
	 * This takes into account that some fields are stored externally in content stores
	 * instead of in the Lucene index.
	 *
	 * @param docId
	 *            the Lucene Document id
	 * @param fieldName
	 *            the name of the field
	 * @param startAtWord where to start getting the content (-1 for start of document, 0 for first word)
	 * @param endAtWord where to end getting the content (-1 for end of document)
	 * @return the field content
	 */
	public String getContent(int docId, String fieldName, int startAtWord, int endAtWord) {
		Document d = document(docId);
		if (!contentStores.exists(fieldName)) {
			// No special content accessor set; assume a stored field
			String content = d.get(fieldName);
			if (content == null)
				throw new IllegalArgumentException("Field not found: " + fieldName);
			return getWordsFromString(content, startAtWord, endAtWord);
		}

		int[] startEnd = startEndWordToCharPos(docId, fieldName, startAtWord, endAtWord);
		return contentStores.getSubstrings(fieldName, d, new int[] { startEnd[0] }, new int[] { startEnd[1] })[0];
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
	private List<HitCharSpan> getCharacterOffsets(int doc, String fieldName, Hits hits) {
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

		List<HitCharSpan> hitspans = new ArrayList<>(starts.length);
		for (int i = 0; i < starts.length; i++) {
			hitspans.add(new HitCharSpan(starts[i], ends[i]));
		}
		return hitspans;
	}

	/**
	 * Convert start/end word positions to char positions.
	 *
	 * @param docId     Lucene Document id
	 * @param fieldName name of the field
	 * @param startAtWord where to start getting the content (-1 for start of document, 0 for first word)
	 * @param endAtWord where to end getting the content (-1 for end of document)
	 * @return the start and end char position as a two element int array
	 *   (with any -1's preserved)
	 */
	private int[] startEndWordToCharPos(int docId, String fieldName,
			int startAtWord, int endAtWord) {
		if (startAtWord == -1 && endAtWord == -1) {
			// No need to translate anything
			return new int[] {-1, -1};
		}

		// Translate word pos to char pos and retrieve content
		// NOTE: this boolean stuff is a bit iffy, but is necessary because
		// getCharacterOffsets doesn't handle -1 to mean start/end of doc.
		// We should probably fix that some time.
		boolean startAtStartOfDoc = startAtWord == -1;
		boolean endAtEndOfDoc = endAtWord == -1;
		int[] starts = new int[] {startAtStartOfDoc ? 0 : startAtWord};
		int[] ends = new int[] {endAtEndOfDoc ? starts[0] : endAtWord};
		getCharacterOffsets(docId, fieldName, starts, ends, true);
		if (startAtStartOfDoc)
			starts[0] = -1;
		if (endAtEndOfDoc)
			ends[0] = -1;
		int[] startEnd = new int[] {starts[0], ends[0]};
		return startEnd;
	}

	/**
	 * Get the contents of a field from a Lucene Document.
	 *
	 * This takes into account that some fields are stored externally in content stores
	 * instead of in the Lucene index.
	 *
	 * @param d
	 *            the Document
	 * @param fieldName
	 *            the name of the field
	 * @return the field content
	 */
	public String getContent(Document d, String fieldName) {
		if (!contentStores.exists(fieldName)) {
			// No special content accessor set; assume a stored field
			return d.get(fieldName);
		}
		// Content accessor set. Use it to retrieve the content.
		return contentStores.getSubstrings(fieldName, d, new int[] { -1 }, new int[] { -1 })[0];
	}

	/**
	 * Get the document contents (original XML).
	 *
	 * @param d
	 *            the Document
	 * @return the field content
	 */
	public String getContent(Document d) {
		return getContent(d, getMainContentsFieldName());
	}

	/**
	 * Get the contents of a field from a Lucene Document.
	 *
	 * This takes into account that some fields are stored externally in content stores
	 * instead of in the Lucene index.
	 *
	 * @param docId
	 *            the Document id
	 * @param fieldName
	 *            the name of the field
	 * @return the field content
	 */
	public String getContent(int docId, String fieldName) {
		return getContent(docId, fieldName, -1, -1);
	}

	/**
	 * Get the document contents (original XML).
	 *
	 * @param docId
	 *            the Document id
	 * @return the field content
	 */
	public String getContent(int docId) {
		return getContent(docId, mainContentsFieldName, -1, -1);
	}

	/**
	 * Get the Lucene index reader we're using.
	 *
	 * @return the Lucene index reader
	 */
	public abstract IndexReader getIndexReader();

	/**
	 * Highlight part of field content with the specified hits,
	 * and make sure it's well-formed.
	 *
	 * Uses &lt;hl&gt;&lt;/hl&gt; tags to highlight the content.
	 *
	 * @param docId
	 *            document to highlight a field from
	 * @param fieldName
	 *            field to highlight
	 * @param hits
	 *            the hits
	 * @param startAtWord where to start highlighting (first word returned)
	 * @param endAtWord where to end highlighting (first word not returned)
	 * @return the highlighted content
	 */
	public String highlightContent(int docId, String fieldName, Hits hits, int startAtWord, int endAtWord) {
		// Get the field content
		int endAtWordForCharPos = endAtWord < 0 ? endAtWord : endAtWord - 1; // if whole content, don't subtract one
		int[] startEndCharPos = startEndWordToCharPos(docId, fieldName, startAtWord, endAtWordForCharPos);
		int startAtChar = startEndCharPos[0];
		int endAtChar = startEndCharPos[1];
		String content = getContentByCharPos(docId, fieldName, startAtChar, endAtChar);

		if (hits == null && startAtWord == -1 && endAtWord == -1) {
			// No hits to highlight, and we've fetched the whole document, so it is
			// well-formed already. Just return as-is.
			return content;
		}

		// Find the character offsets for the hits and highlight
		List<HitCharSpan> hitspans = null;
		if (hits != null) // if hits == null, we still want the highlighter to make it well-formed
			hitspans = getCharacterOffsets(docId, fieldName, hits);
		XmlHighlighter hl = new XmlHighlighter();
		hl.setUnbalancedTagsStrategy(getDefaultUnbalancedTagsStrategy());
		if (startAtChar == -1)
			startAtChar = 0;
		return hl.highlight(content, hitspans, startAtChar);
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
		return highlightContent(docId, fieldName, hits, -1, -1);
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
		return highlightContent(docId, getMainContentsFieldName(), hits, -1, -1);
	}

	/**
	 * Get the content store for a field name.
	 *
	 * @param fieldName the field name
	 * @return the content store, or null if there is no content store for this field
	 */
	public ContentStore getContentStore(String fieldName) {
		ContentStore cs = contentStores.get(fieldName);
		if (indexMode && cs == null) {
			// Index mode. Create new content store or open existing one.
			return openContentStore(fieldName);
		}
		return cs;
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
	protected void registerContentStore(String fieldName, ContentStore contentStore) {
		contentStores.put(fieldName, contentStore);
	}

	protected abstract ContentStore openContentStore(String fieldName);

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
			forwardIndex = openForwardIndex(fieldPropName);
			if (forwardIndex != null)
				addForwardIndex(fieldPropName, forwardIndex);
		}
		return forwardIndex;
	}

	protected void addForwardIndex(String fieldPropName, ForwardIndex forwardIndex) {
		forwardIndices.put(fieldPropName, forwardIndex);
	}

	protected abstract ForwardIndex openForwardIndex(String fieldPropName);

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
		if (!contentStores.exists(fieldName)) {
			String[] content;
			// No special content accessor set; assume a non-complex stored field
			String luceneName = fieldName; // <- non-complex, so this works
			String fieldContent = d.get(luceneName);
			content = new String[starts.length];
			for (int i = 0; i < starts.length; i++) {
				content[i] = fieldContent.substring(starts[i], ends[i]);
			}
			return content;
		}
		// Content accessor set. Use it to retrieve the content.
		return contentStores.getSubstrings(fieldName, d, starts, ends);
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
	 * @param hl
	 * @return the list of concordances
	 */
	public List<Concordance> makeConcordancesFromContentStore(int doc, String fieldName, int[] startsOfWords,
			int[] endsOfWords, XmlHighlighter hl) {
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
		List<Concordance> rv = new ArrayList<>();
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

			// Make each fragment well-formed
			hitText = hl.makeWellFormed(hitText);
			leftContext = hl.makeWellFormed(leftContext);
			rightContext = hl.makeWellFormed(rightContext);

			rv.add(new Concordance(new String[] { leftContext, hitText, rightContext }));
		}
		return rv;
	}


	/**
	 * Indicate how to use the forward indices to build concordances.
	 *
	 * Call this method to set the default for hit sets; call the method in Hits
	 * to change it for a single hit set.
	 *
	 * @param wordFI FI to use as the text content of the &lt;w/&gt; tags (default "word"; null for no text content)
	 * @param punctFI FI to use as the text content between &lt;w/&gt; tags (default "punct"; null for just a space)
	 * @param attrFI FIs to use as the attributes of the &lt;w/&gt; tags (null for all other FIs)
	 * @deprecated use hitsSettings().setConcordanceProperties()
	 */
	@Deprecated
	public void setForwardIndexConcordanceParameters(String wordFI, String punctFI, Collection<String> attrFI) {
		setConcordanceXmlProperties(wordFI, punctFI, attrFI);
	}

	/**
	 * Indicate how to use the forward indices to build concordances.
	 *
	 * Only applies if you're building concordances from the forward index.
	 *
	 * Call this method to set the default for hit sets; call the method in Hits
	 * to change it for a single hit set.
	 *
	 * @param wordFI FI to use as the text content of the &lt;w/&gt; tags (default "word"; null for no text content)
	 * @param punctFI FI to use as the text content between &lt;w/&gt; tags (default "punct"; null for just a space)
	 * @param attrFI FIs to use as the attributes of the &lt;w/&gt; tags (null for all other FIs)
	 * @deprecated use hitsSettings().setConcordanceProperties()
	 */
	@Deprecated
	public void setConcordanceXmlProperties(String wordFI, String punctFI,
			Collection<String> attrFI) {
		hitsSettings().setConcordanceProperties(wordFI, punctFI, attrFI);
	}


	/**
	 * Get the default context size used for building concordances
	 *
	 * @return the context size
	 * @deprecated use hitsSettings().contextSize()
	 */
	@Deprecated
	public int getDefaultContextSize() {
		return hitsSettings().contextSize();
	}

	/**
	 * Set the default context size to use for building concordances
	 *
	 * @param defaultContextSize
	 *            the context size
	 * @deprecated use hitsSettings().setContextSize()
	 */
	@Deprecated
	public void setDefaultContextSize(int defaultContextSize) {
		hitsSettings().setContextSize(defaultContextSize);
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
		return ContentStore.open(indexXmlDir, create);
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
			throw new IllegalArgumentException("Field " + fieldPropName + " has no forward index!");
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
		return getTerms(ComplexFieldUtil.mainPropertyField(getIndexStructure(), getMainContentsFieldName()));
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
	public abstract QueryExecutionContext getDefaultExecutionContext(String fieldName);

	/**
	 * Get the default initial query execution context.
	 *
	 * Uses the default contents field.
	 *
	 * @return the query execution context
	 */
	public QueryExecutionContext getDefaultExecutionContext() {
		return getDefaultExecutionContext(getMainContentsFieldName());
	}

	public abstract String getIndexName();

	public abstract IndexWriter openIndexWriter(File indexDir, boolean create, Analyzer useAnalyzer)
			throws IOException, CorruptIndexException, LockObtainFailedException;

	public abstract IndexWriter getWriter();

	public abstract File getIndexDirectory();

	/** Deletes documents matching a query from the BlackLab index.
	 *
	 * This deletes the documents from the Lucene index, the forward indices and the content store(s).
	 * @param q the query
	 */
	public abstract void delete(Query q);

	/**
	 * Get the analyzer for indexing and searching.
	 * @return the analyzer
	 */
	public Analyzer getAnalyzer() {
		return analyzer;
	}

	/**
	 * Perform a document query only (no hits)
	 * @param documentFilterQuery the document-level query
	 * @return the matching documents
	 */
	public DocResults queryDocuments(Query documentFilterQuery) {
		return DocResults._fromQuery(this, documentFilterQuery);
	}

	/**
	 * Return the list of terms that occur in a field.
	 *
	 * @param fieldName the field
	 * @param maxResults maximum number to return (or HitsSettings.UNLIMITED (== -1) for no limit)
	 * @return the matching terms
	 */
	public abstract List<String> getFieldTerms(String fieldName, int maxResults);

	public String getMainContentsFieldName() {
		return mainContentsFieldName;
	}

	/**
	 * @return the word property used for concordances
	 * @deprecated use hitsSettings().concWordProp()
	 */
	@Deprecated
	public String getConcWordFI() {
		return hitsSettings().concWordProp();
	}

	/**
	 * @return the punctuation property used for concordances
	 * @deprecated use hitsSettings().concPunctProp()
	 */
	@Deprecated
	public String getConcPunctFI() {
		return hitsSettings().concPunctProp();
	}

	/**
	 * @return the extra attribute properties used for concordances
	 * @deprecated use hitsSettings().concAttrProps()
	 */
	@Deprecated
	public Collection<String> getConcAttrFI() {
		return hitsSettings().concAttrProps();
	}

	public abstract IndexSearcher getIndexSearcher();

	protected void deleteFromForwardIndices(Document d) {
		// Delete this document in all forward indices
		for (Map.Entry<String, ForwardIndex> e: forwardIndices.entrySet()) {
			String fieldName = e.getKey();
			ForwardIndex fi = e.getValue();
			int fiid = Integer.parseInt(d.get(ComplexFieldUtil
					.forwardIndexIdField(fieldName)));
			fi.deleteDocument(fiid);
		}
	}

	public Map<String, ForwardIndex> getForwardIndices() {
		return forwardIndices;
	}

}