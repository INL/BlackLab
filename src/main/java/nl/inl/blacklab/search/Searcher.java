package nl.inl.blacklab.search;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.text.Collator;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.store.LockObtainFailedException;

import nl.inl.blacklab.analysis.BLDutchAnalyzer;
import nl.inl.blacklab.analysis.BLNonTokenizingAnalyzer;
import nl.inl.blacklab.analysis.BLStandardAnalyzer;
import nl.inl.blacklab.analysis.BLWhitespaceAnalyzer;
import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.highlight.XmlHighlighter;
import nl.inl.blacklab.highlight.XmlHighlighter.UnbalancedTagsStrategy;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.util.VersionFile;

public abstract class Searcher {

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
	 *   JAR, or JAR was not created with the Ant buildscript).
	 */
	public static String getBlackLabBuildTime() {
		try {
			URL res = Searcher.class.getResource(Searcher.class.getSimpleName() + ".class");
			URLConnection conn = res.openConnection();
			if (!(conn instanceof JarURLConnection)) {
				// Not running from a JAR, no manifest to read
				return "UNKNOWN";
			}
			JarURLConnection jarConn = (JarURLConnection) res.openConnection();
			Manifest mf = jarConn.getManifest();
			String value = null;
			if (mf != null) {
				Attributes atts = mf.getMainAttributes();
				if (atts != null) {
					value = atts.getValue("Build-Time");
					if (value == null)
						value = atts.getValue("Build-Date"); // Old name for this info
				}
			}
			return value == null ? "UNKNOWN" : value;
		} catch (IOException e) {
			throw new RuntimeException("Could not read build date from manifest", e);
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

	/** Complex field name for default contents field */
	public static final String DEFAULT_CONTENTS_FIELD_NAME = "contents";

	/**
	 * How do we fix well-formedness for snippets of XML?
	 * @return the setting: either adding or removing unbalanced tags
	 */
	public abstract UnbalancedTagsStrategy getDefaultUnbalancedTagsStrategy();

	/**
	 * Set how to fix well-formedness for snippets of XML.
	 * @param strategy the setting: either adding or removing unbalanced tags
	 */
	public abstract void setDefaultUnbalancedTagsStrategy(UnbalancedTagsStrategy strategy);

	/**
	 * Are we making concordances using the forward index (true) or using
	 * the content store (false)? Forward index is more efficient but returns
	 * concordances that don't include XML tags.
	 *
	 * @return true iff we use the forward index for making concordances.
	 * @deprecated use getDefaultConcordanceType
	 */
	@Deprecated
	public boolean getMakeConcordancesFromForwardIndex() {
		return getDefaultConcordanceType() == ConcordanceType.FORWARD_INDEX;
	}

	public abstract ConcordanceType getDefaultConcordanceType();

	public abstract void setDefaultConcordanceType(ConcordanceType type);

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
	 * @deprecated use setDefaultConcordanceType()
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
	public abstract void close();

	/**
	 * Get information about the structure of the BlackLab index.
	 *
	 * @return the structure object
	 */
	public abstract IndexStructure getIndexStructure();

	/**
	 * Retrieve a Lucene Document object from the index.
	 *
	 * NOTE: you must check if the document isn't deleted using Search.isDeleted()
	 * first! Lucene 4.0+ allows you to retrieve deleted documents, making you
	 * responsible for checking whether documents are deleted or not.
	 *
	 * @param doc
	 *            the document id
	 * @return the Lucene Document
	 * @throws RuntimeException if the document doesn't exist (use maxDoc() and isDeleted() to check first!)
	 */
	public abstract Document document(int doc);

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

	public abstract SpanQuery filterDocuments(SpanQuery query, Filter filter);

	public abstract SpanQuery createSpanQuery(TextPattern pattern, String fieldName, Filter filter);

	public abstract SpanQuery createSpanQuery(TextPattern pattern, Filter filter);

	public abstract SpanQuery createSpanQuery(TextPattern pattern, String fieldName);

	public abstract SpanQuery createSpanQuery(TextPattern pattern);

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
	public abstract Hits find(SpanQuery query, String fieldNameConc) throws BooleanQuery.TooManyClauses;

	/**
	 * Find hits for a pattern in a field.
	 *
	 * @param query
	 *            the pattern to find
	 * @return the hits found
	 * @throws BooleanQuery.TooManyClauses
	 *             if a wildcard or regular expression term is overly broad
	 */
	public abstract Hits find(SpanQuery query) throws BooleanQuery.TooManyClauses;

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
	public abstract Hits find(TextPattern pattern, String fieldName, Filter filter) throws BooleanQuery.TooManyClauses;

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
	public abstract Hits find(TextPattern pattern, Filter filter) throws BooleanQuery.TooManyClauses;

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
	public abstract Hits find(TextPattern pattern, String fieldName) throws BooleanQuery.TooManyClauses;

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
	public abstract Hits find(TextPattern pattern) throws BooleanQuery.TooManyClauses;

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
	public abstract Scorer findDocScores(Query q);

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
	public abstract TopDocs findTopDocs(Query q, int n);

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

	public abstract DocContentsFromForwardIndex getContentFromForwardIndex(int docId, String fieldName, int startAtWord, int endAtWord);

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
	public abstract String getContent(int docId, String fieldName, int startAtWord, int endAtWord);

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
	public abstract String getContentByCharPos(int docId, String fieldName, int startAtChar, int endAtChar);

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
	 * @deprecated use version that takes a docId
	 */
	@Deprecated
	public String getContent(Document d, String fieldName) {
		throw new UnsupportedOperationException("Deprecated");
	}

	/**
	 * Get the document contents (original XML).
	 *
	 * @param d
	 *            the Document
	 * @return the field content
	 * @deprecated use version that takes a docId
	 */
	@Deprecated
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
	public abstract String getContent(int docId, String fieldName);

	/**
	 * Get the document contents (original XML).
	 *
	 * @param docId
	 *            the Document id
	 * @return the field content
	 */
	public abstract String getContent(int docId);

	/**
	 * Get the Lucene index reader we're using.
	 *
	 * @return the Lucene index reader
	 */
	public abstract DirectoryReader getIndexReader();

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
	public abstract String highlightContent(int docId, String fieldName, Hits hits, int startAtWord, int endAtWord);

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
	public abstract String highlightContent(int docId, String fieldName, Hits hits);

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
	public abstract String highlightContent(int docId, Hits hits);

	/**
	 * Get the content store for a field name.
	 *
	 * @param fieldName the field name
	 * @return the content store, or null if there is no content store for this field
	 */
	public abstract ContentStore getContentStore(String fieldName);

	/**
	 * Set the collator used for sorting.
	 *
	 * The default collator is for English.
	 *
	 * @param collator
	 *            the collator
	 */
	public abstract void setCollator(Collator collator);

	/**
	 * Get the collator being used for sorting.
	 *
	 * @return the collator
	 */
	public abstract Collator getCollator();

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
	public abstract ForwardIndex getForwardIndex(String fieldPropName);

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
	public abstract List<Concordance> makeConcordancesFromContentStore(int doc, String fieldName, int[] startsOfWords,
			int[] endsOfWords, XmlHighlighter hl);

	/**
	 * Indicate how to use the forward indices to build concordances.
	 *
	 * Call this method to set the default for hit sets; call the method in Hits
	 * to change it for a single hit set.
	 *
	 * @param wordFI FI to use as the text content of the &lt;w/&gt; tags (default "word"; null for no text content)
	 * @param punctFI FI to use as the text content between &lt;w/&gt; tags (default "punct"; null for just a space)
	 * @param attrFI FIs to use as the attributes of the &lt;w/&gt; tags (null for all other FIs)
	 * @deprecated renamed to setConcordanceXmlProperties
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
	 */
	public abstract void setConcordanceXmlProperties(String wordFI, String punctFI, Collection<String> attrFI);

	/**
	 * Get the default context size used for building concordances
	 *
	 * @return the context size
	 */
	public abstract int getDefaultContextSize();

	/**
	 * Set the default context size to use for building concordances
	 *
	 * @param defaultContextSize
	 *            the context size
	 */
	public abstract void setDefaultContextSize(int defaultContextSize);

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
	public abstract ContentStore openContentStore(File indexXmlDir, boolean create);

	/**
	 * Factory method to create a directory content store.
	 *
	 * @param indexXmlDir
	 *            the content store directory
	 * @return the content store
	 */
	public abstract ContentStore openContentStore(File indexXmlDir);

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
	public abstract Terms getTerms(String fieldPropName);

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
	public abstract Terms getTerms();

	public abstract String getContentsFieldMainPropName();

	public abstract boolean isDefaultSearchCaseSensitive();

	public abstract boolean isDefaultSearchDiacriticsSensitive();

	public abstract void setDefaultSearchSensitive(boolean b);

	public abstract void setDefaultSearchSensitive(boolean caseSensitive, boolean diacriticsSensitive);

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
	public abstract QueryExecutionContext getDefaultExecutionContext();

	public abstract String getIndexName();

	public abstract IndexWriter openIndexWriter(File indexDir, boolean create, Analyzer analyzer)
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
	public abstract Analyzer getAnalyzer();

	/**
	 * Get the analyzer to use for indexing.
	 * (strips things like wildcards, etc.)
	 * @return the analyzer
	 * @deprecated use getAnalyzer() (we can use the same analyzer for indexing and searching after all because wildcard queries are never analyzed)
	 */
	@Deprecated
	public Analyzer getIndexAnalyzer() {
		return getAnalyzer();
	}

	/**
	 * Get the analyzer to use for parsing document filters while searching.
	 * (leaves wildcards alone)
	 * @return the analyzer
	 * @deprecated use getAnalyzer() (we can use the same analyzer for indexing and searching after all because wildcard queries are never analyzed)
	 */
	@Deprecated
	public Analyzer getSearchAnalyzer() {
		return getAnalyzer();
	}

	/**
	 * Perform a document query only (no hits)
	 * @param documentFilterQuery the document-level query
	 * @return the matching documents
	 */
	public abstract DocResults queryDocuments(Query documentFilterQuery);

	/**
	 * Determine the term frequencies in a set of documents (defined by the filter query)
	 *
	 * @param documentFilterQuery what set of documents to get the term frequencies for
	 * @param fieldName complex field name, i.e. contents
	 * @param propName property name, i.e. word, lemma, pos, etc.
	 * @param altName alternative name, i.e. s, i (case-sensitivity)
	 * @return the term frequency map
	 */
	public abstract Map<String, Integer> termFrequencies(Query documentFilterQuery, String fieldName, String propName, String altName);

	/**
	 * Perform a document query and collect the results through a Collector.
	 * @param query query to execute
	 * @param collector object that receives each document hit
	 */
	public abstract void collectDocuments(Query query, Collector collector);

	/**
	 * Return the list of terms that occur in a field.
	 *
	 * @param fieldName the field
	 * @param maxResults maximum number to return (or -1 for no limit)
	 * @return the matching terms
	 */
	public abstract List<String> getFieldTerms(String fieldName, int maxResults);

	public abstract String getMainContentsFieldName();

	public abstract String getConcWordFI();

	public abstract String getConcPunctFI();

	public abstract Collection<String> getConcAttrFI();

	public abstract Map<String, ForwardIndex> getForwardIndices();

	public abstract IndexSearcher getIndexSearcher();

}