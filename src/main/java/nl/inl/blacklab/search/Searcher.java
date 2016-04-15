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

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import nl.inl.blacklab.analysis.BLDutchAnalyzer;
import nl.inl.blacklab.analysis.BLNonTokenizingAnalyzer;
import nl.inl.blacklab.analysis.BLStandardAnalyzer;
import nl.inl.blacklab.analysis.BLWhitespaceAnalyzer;
import nl.inl.blacklab.externalstorage.ContentAccessorContentStore;
import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.externalstorage.ContentStoreDirAbstract;
import nl.inl.blacklab.externalstorage.ContentStoreDirFixedBlock;
import nl.inl.blacklab.externalstorage.ContentStoreDirUtf8;
import nl.inl.blacklab.externalstorage.ContentStoreDirZip;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.highlight.XmlHighlighter;
import nl.inl.blacklab.highlight.XmlHighlighter.HitCharSpan;
import nl.inl.blacklab.highlight.XmlHighlighter.UnbalancedTagsStrategy;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.perdocument.DocResults;
import nl.inl.blacklab.search.indexstructure.ComplexFieldDesc;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc;
import nl.inl.blacklab.search.indexstructure.PropertyDesc;
import nl.inl.blacklab.search.lucene.SpanQueryFiltered;
import nl.inl.blacklab.search.lucene.TextPatternTranslatorSpanQuery;
import nl.inl.util.ExUtil;
import nl.inl.util.LogUtil;
import nl.inl.util.LuceneUtil;
import nl.inl.util.Utilities;
import nl.inl.util.VersionFile;

import org.apache.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.SlowCompositeReaderWrapper;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Collector;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Filter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.Bits;

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
public class Searcher implements Closeable {

	protected static final Logger logger = Logger.getLogger(Searcher.class);

	/** Complex field name for default contents field */
	public static final String DEFAULT_CONTENTS_FIELD_NAME = "contents";

	/** The collator to use for sorting. Defaults to English collator. */
	private static Collator defaultCollator = Collator.getInstance(new Locale("en", "GB"));

	/** Analyzer based on WhitespaceTokenizer */
	private static BLWhitespaceAnalyzer whitespaceAnalyzer;

	/** Analyzer for Dutch and other Latin script languages */
	private static BLDutchAnalyzer defaultAnalyzer;

	/** Analyzer based on StandardTokenizer */
	private static BLStandardAnalyzer standardAnalyzer;

	/** Analyzer that doesn't tokenize */
	private static BLNonTokenizingAnalyzer nonTokenizingAnalyzer;

	static {
		// Create the various analyzer objects we'll be using for metadata fields.
		whitespaceAnalyzer = new BLWhitespaceAnalyzer();
		defaultAnalyzer = new BLDutchAnalyzer();
		standardAnalyzer = new BLStandardAnalyzer();
		nonTokenizingAnalyzer = new BLNonTokenizingAnalyzer();
	}

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
	private Map<String, ContentAccessor> contentAccessors = new HashMap<>();

	/**
	 * ForwardIndices allow us to quickly find what token occurs at a specific position. This speeds
	 * up grouping and sorting. There may be several indices on a complex field, e.g.: word form,
	 * lemma, part of speech.
	 *
	 * Indexed by property name.
	 */
	private Map<String, ForwardIndex> forwardIndices = new HashMap<>();

	/**
	 * The Lucene index reader
	 */
	private DirectoryReader reader;

	/**
	 * The Lucene IndexSearcher, for dealing with non-Span queries (for per-document scoring)
	 */
	private IndexSearcher indexSearcher;

	/**
	 * Name of the main contents field (used as default parameter value for many methods)
	 */
	public String mainContentsFieldName;

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
	 *  content store? Generating them from the forward index is more
	 *  efficient.
	 *
	 *  This is set to true for all modern indices.
	 *  (to be precise, it's set to true iff a punctuation forward index is present)
	 *
	 *  This setting controls the default. You don't have to set this to false if you
	 *  sometimes want concordances from the content store; you can specifically request
	 *  those when you need them.
	 */
	private ConcordanceType defaultConcsType = ConcordanceType.CONTENT_STORE;
	//private boolean concordancesFromForwardIndex = false;

	/** Forward index to use as text context of &lt;w/&gt; tags in concordances (words; null = no text content) */
	private String concWordFI = "word";

	/** Forward index to use as text context between &lt;w/&gt; tags in concordances (punctuation+whitespace; null = just a space) */
	private String concPunctFI = ComplexFieldUtil.PUNCTUATION_PROP_NAME;

	/** Forward indices to use as attributes of &lt;w/&gt; tags in concordances (null = the rest) */
	private Collection<String> concAttrFI = null; // all other FIs are attributes

	/** How we fix well-formedness for snippets of XML: by adding or removing unbalanced tags */
	private UnbalancedTagsStrategy defaultUnbalancedTagsStrategy = UnbalancedTagsStrategy.ADD_TAG;

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

	public ConcordanceType getDefaultConcordanceType() {
		return defaultConcsType;
	}

	public void setDefaultConcordanceType(ConcordanceType type) {
		defaultConcsType = type;
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
	 * @deprecated use setDefaultConcordanceType()
	 */
	@Deprecated
	public void setMakeConcordancesFromForwardIndex(boolean concordancesFromForwardIndex) {
		setDefaultConcordanceType(concordancesFromForwardIndex ? ConcordanceType.FORWARD_INDEX : ConcordanceType.CONTENT_STORE);
	}

	/** If true, we want to add/delete documents. If false, we're just searching. */
	private boolean indexMode = false;

	/** If true, we've just created a new index. New indices cannot be searched, only added to. */
	private boolean isEmptyIndex = false;

	/** The index writer. Only valid in indexMode. */
	private IndexWriter indexWriter = null;

	/** Thread that automatically warms up the forward indices, if enabled. */
	private Thread buildTermIndicesThread;

	/** Analyzer used for indexing our metadata fields */
	private Analyzer analyzer;

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
		return new Searcher(indexDir, true, createNewIndex, (File)null);
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
		return new Searcher(indexDir, true, createNewIndex, indexTemplateFile);
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
		return new Searcher(indexDir, false, false, (File)null);
	}

	/**
	 * Open an index.
	 *
	 * @param indexDir the index directory
	 * @param indexMode if true, open in index mode; if false, open in search mode.
	 * @param createNewIndex if true, delete existing index in this location if it exists.
	 * @param indexTemplateFile JSON file to use as template for index structure / metadata
	 *   (if creating new index)
	 * @throws IOException
	 */
	private Searcher(File indexDir, boolean indexMode, boolean createNewIndex, File indexTemplateFile)
			throws IOException {
		this.indexMode = indexMode;

		if (!indexMode && createNewIndex)
			throw new RuntimeException("Cannot create new index, not in index mode");

		if (!createNewIndex) {
			if (!indexMode || VersionFile.exists(indexDir)) {
				if (!isIndex(indexDir)) {
					throw new RuntimeException("BlackLab index has wrong type or version! "
							+ VersionFile.report(indexDir));
				}
			}
		}

		// If we didn't provide log4j.properties on the classpath, initialise it using default settings.
		LogUtil.initLog4jIfNotAlready();

		logger.debug("Constructing Searcher...");

		if (indexMode) {
			indexWriter = openIndexWriter(indexDir, createNewIndex, null);
			reader = DirectoryReader.open(indexWriter, false);
		} else {
			// Open Lucene index
			Path indexPath = indexDir.toPath();
			while (Files.isSymbolicLink(indexPath)) {
				// Resolve symlinks, as FSDirectory.open() can't handle them
				indexPath = Files.readSymbolicLink(indexPath);
			}
			reader = DirectoryReader.open(FSDirectory.open(indexPath));
		}
		this.indexLocation = indexDir;

		// Determine the index structure
		indexStructure = new IndexStructure(reader, indexDir, createNewIndex, indexTemplateFile);
		isEmptyIndex = indexStructure.isNewIndex();

		// TODO: we need to create the analyzer before opening the index, because
		//   we can't change the analyzer attached to the IndexWriter (and passing a different
		//   analyzer in addDocument() is going away in Lucene 5.x).
		//   For now, if we're in index mode, we re-open the index with the analyzer we determined.
		createAnalyzers();

		if (indexMode) {
			// Re-open the IndexWriter with the analyzer we've created above (see comment above)
			reader.close();
			reader = null;
			indexWriter.close();
			indexWriter = null;
			indexWriter = openIndexWriter(indexDir, createNewIndex, analyzer);
			reader = DirectoryReader.open(indexWriter, false);
		}

		// Detect and open the ContentStore for the contents field
		if (!createNewIndex) {
			ComplexFieldDesc mainContentsField = indexStructure.getMainContentsField();
			if (mainContentsField == null) {
				if (!indexMode) {
					if (!isEmptyIndex)
						throw new RuntimeException("Could not detect main contents field");

					// Empty index. Set a default name for the contents field.
					// Searching an empty index will fail and should not be attempted.
					this.mainContentsFieldName = Searcher.DEFAULT_CONTENTS_FIELD_NAME;
				}
			} else {
				this.mainContentsFieldName = mainContentsField.getName();

				// See if we have a punctuation forward index. If we do,
				// default to creating concordances using that.
				if (mainContentsField.hasPunctuation()) {
					defaultConcsType = ConcordanceType.FORWARD_INDEX;
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
						registerContentStore(cfn, openContentStore(dir));
					}
				}
			}
		}

		indexSearcher = new IndexSearcher(reader);

		// Make sure large wildcard/regex expansions succeed
		BooleanQuery.setMaxClauseCount(100000);

		// Open the forward indices
		if (!createNewIndex)
			openForwardIndices();
		logger.debug("Done.");
	}

	/**
	 * Is this a newly created, empty index?
	 * @return true if it is, false if not
	 */
	public boolean isEmpty() {
		return isEmptyIndex;
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
	 * Open an index.
	 *
	 * @param indexDir the index directory
	 * @param indexMode if true, open in index mode; if false, open in search mode.
	 * @param createNewIndex if true, delete existing index in this location if it exists.
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	private Searcher(File indexDir, boolean indexMode, boolean createNewIndex)
			throws CorruptIndexException, IOException {
		this(indexDir, indexMode, createNewIndex, (File)null);
	}

	private void createAnalyzers() {
		Map<String, Analyzer> fieldAnalyzers = new HashMap<>();
		fieldAnalyzers.put("fromInputFile", getAnalyzerInstance("nontokenizing"));
		Analyzer baseAnalyzer = getAnalyzerInstance(indexStructure.getDefaultAnalyzerName());
		for (String fieldName: indexStructure.getMetadataFields()) {
			MetadataFieldDesc fd = indexStructure.getMetadataFieldDesc(fieldName);
			String analyzerName = fd.getAnalyzerName();
			if (analyzerName.length() > 0 && !analyzerName.equalsIgnoreCase("DEFAULT")) {
				Analyzer fieldAnalyzer = getAnalyzerInstance(analyzerName);
				if (fieldAnalyzer == null) {
					logger.error("Unknown analyzer name " + analyzerName + " for field " + fieldName);
				} else {
					if (fieldAnalyzer != baseAnalyzer)
						fieldAnalyzers.put(fieldName, fieldAnalyzer);
				}
			}
		}

		analyzer = new PerFieldAnalyzerWrapper(baseAnalyzer, fieldAnalyzers);
	}

	/**
	 * Construct a Searcher object, the main search interface on a BlackLab index.
	 *
	 * @param indexDir
	 *            the index directory
	 * @throws CorruptIndexException
	 * @throws IOException
	 * @deprecated use Searcher.open(File)
	 */
	@Deprecated
	public Searcher(File indexDir) throws CorruptIndexException, IOException {
		this(indexDir, false, false);
	}

	/**
	 * Call this to roll back any changes made to the index this session.
	 * Calling close() will automatically commit any changes. If you call this
	 * method, then call close(), no changes will be committed.
	 */
	public void rollback() {
		try {
			indexWriter.rollback();
			indexWriter = null;
		} catch (IOException e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Finalize the Searcher object. This closes the IndexSearcher and (depending on the constructor
	 * used) may also close the index reader.
	 */
	@Override
	public void close() {
		try {
			reader.close();
			if (indexWriter != null) {
				indexWriter.commit();
				indexWriter.close();
			}

			// See if the forward index warmup thread is running, and if so, stop it
			if (buildTermIndicesThread != null && buildTermIndicesThread.isAlive()) {
				buildTermIndicesThread.interrupt();

				// Wait for a maximum of a second for the thread to close down gracefully
				int i = 0;
				while (buildTermIndicesThread.isAlive() && i < 10) {
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						// OK
					}
					i++;
				}
			}

			// Close the forward indices
			for (ForwardIndex fi: forwardIndices.values()) {
				fi.close();
			}

			// Close the content accessor(s)
			// (the ContentStore, and possibly other content accessors
			// (although that feature is not used right now))
			for (ContentAccessor ca: contentAccessors.values()) {
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
	 * NOTE: you must check if the document isn't deleted using Search.isDeleted()
	 * first! Lucene 4.0+ allows you to retrieve deleted documents, making you
	 * responsible for checking whether documents are deleted or not.
	 *
	 * @param doc
	 *            the document id
	 * @return the Lucene Document
	 * @throws RuntimeException if the document doesn't exist (use maxDoc() and isDeleted() to check first!)
	 */
	public Document document(int doc) {
		try {
			if (doc < 0)
				throw new RuntimeException("Negative document id");
			if (doc >= reader.maxDoc())
				throw new RuntimeException("Document id >= maxDoc");
			return reader.document(doc);
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Checks if a document has been deleted from the index
	 * @param doc the document id
	 * @return true iff it has been deleted
	 */
	public boolean isDeleted(int doc) {
		Bits liveDocs = MultiFields.getLiveDocs(reader);
		return liveDocs != null && !liveDocs.get(doc);
	}

	/**
	 * Returns one more than the highest document id
	 * @return one more than the highest document id
	 */
	public int maxDoc() {
		return reader.maxDoc();
	}

	public SpanQuery filterDocuments(SpanQuery query, Filter filter) {
		return new SpanQueryFiltered(query, filter);
	}

	public SpanQuery createSpanQuery(TextPattern pattern, String fieldName, Filter filter) {
		// Convert to SpanQuery
		pattern = pattern.rewrite();
		TextPatternTranslatorSpanQuery spanQueryTranslator = new TextPatternTranslatorSpanQuery();
		SpanQuery spanQuery = pattern.translate(spanQueryTranslator,
				getDefaultExecutionContext(fieldName));
		if (filter != null)
			spanQuery = new SpanQueryFiltered(spanQuery, filter);
		return spanQuery;
	}

	public SpanQuery createSpanQuery(TextPattern pattern, Filter filter) {
		return createSpanQuery(pattern, mainContentsFieldName, filter);
	}

	public SpanQuery createSpanQuery(TextPattern pattern, String fieldName) {
		return createSpanQuery(pattern, fieldName, (Filter)null);
	}

	public SpanQuery createSpanQuery(TextPattern pattern) {
		return createSpanQuery(pattern, mainContentsFieldName, (Filter)null);
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
		return new Hits(this, mainContentsFieldName, query);
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
		return find(pattern, mainContentsFieldName, filter);
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
		return find(pattern, mainContentsFieldName, null);
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
			Weight w = indexSearcher.createNormalizedWeight(q, true);
			LeafReader scrw = SlowCompositeReaderWrapper.wrap(reader);
			Scorer sc = w.scorer(scrw.getContext(), MultiFields.getLiveDocs(reader));
			return sc;
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
			return indexSearcher.search(q, n);
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
	void getCharacterOffsets(int doc, String fieldName, int[] startsOfWords, int[] endsOfWords,
			boolean fillInDefaultsIfNotFound) {

		if (startsOfWords.length == 0)
			return; // nothing to do
		try {
			// Determine lowest and highest word position we'd like to know something about.
			// This saves a little bit of time for large result sets.
			int minP = -1, maxP = -1;
			int numStarts = startsOfWords.length;
			int numEnds = endsOfWords.length;
			for (int i = 0; i < numStarts; i++) {
				if (startsOfWords[i] < minP || minP == -1)
					minP = startsOfWords[i];
				if (startsOfWords[i] > maxP)
					maxP = startsOfWords[i];
			}
			for (int i = 0; i < numEnds; i++) {
				if (endsOfWords[i] < minP || minP == -1)
					minP = endsOfWords[i];
				if (endsOfWords[i] > maxP)
					maxP = endsOfWords[i];
			}
			if (minP < 0 || maxP < 0)
				throw new RuntimeException("Can't determine min and max positions");

			String fieldPropName = ComplexFieldUtil.mainPropertyOffsetsField(indexStructure, fieldName);

			org.apache.lucene.index.Terms terms = reader.getTermVector(doc, fieldPropName);
			if (terms == null)
				throw new RuntimeException("Field " + fieldPropName + " in doc " + doc + " has no term vector");
			if (!terms.hasPositions())
				throw new RuntimeException("Field " + fieldPropName + " in doc " + doc + " has no character postion information");

			//int lowestPos = -1, highestPos = -1;
			int lowestPosFirstChar = -1, highestPosLastChar = -1;
			int total = numStarts + numEnds;
			boolean[] done = new boolean[total]; // NOTE: array is automatically initialized to zeroes!
			int found = 0;

			// Iterate over terms
			TermsEnum termsEnum = terms.iterator();
			while (termsEnum.next() != null) {
				PostingsEnum dpe = termsEnum.postings(null, null, PostingsEnum.POSITIONS);

				// Iterate over docs containing this term (NOTE: should be only one doc!)
				while (dpe.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
					// Iterate over positions of this term in this doc
					int positionsRead = 0;
					int numberOfPositions = dpe.freq();
					while (positionsRead < numberOfPositions) {
						int position = dpe.nextPosition();
						if (position == -1)
							break;
						positionsRead++;

						// Keep track of the lowest and highest char pos, so
						// we can fill in the character positions we didn't find
						int startOffset = dpe.startOffset();
						if (startOffset < lowestPosFirstChar || lowestPosFirstChar == -1) {
							lowestPosFirstChar = startOffset;
						}
						int endOffset = dpe.endOffset();
						if (endOffset > highestPosLastChar) {
							highestPosLastChar = endOffset;
						}

						// We've calculated the min and max word positions in advance, so
						// we know we can skip this position if it's outside the range we're interested in.
						// (Saves a little time for large result sets)
						if (position < minP || position > maxP) {
							continue;
						}

						for (int m = 0; m < numStarts; m++) {
							if (!done[m] && position == startsOfWords[m]) {
								done[m] = true;
								startsOfWords[m] = startOffset;
								found++;
							}
						}
						for (int m = 0; m < numEnds; m++) {
							if (!done[numStarts + m] && position == endsOfWords[m]) {
								done[numStarts + m] = true;
								endsOfWords[m] = endOffset;
								found++;
							}
						}

						// NOTE: we might be tempted to break here if found == total,
						// but that would foul up our calculation of highestPosLastChar and
						// lowestPosFirstChar.
					}
				}

			}
			if (found < total) {
				if (!fillInDefaultsIfNotFound)
					throw new RuntimeException("Could not find all character offsets!");

				if (lowestPosFirstChar < 0 || highestPosLastChar < 0)
					throw new RuntimeException("Could not find default char positions!");

				for (int m = 0; m < numStarts; m++) {
					if (!done[m])
						startsOfWords[m] = lowestPosFirstChar;
				}
				for (int m = 0; m < numEnds; m++) {
					if (!done[numStarts + m])
						endsOfWords[m] = highestPosLastChar;
				}
			}

		} catch (IOException e) {
			throw ExUtil.wrapRuntimeException(e);
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

	public DocContentsFromForwardIndex getContentFromForwardIndex(int docId, String fieldName, int startAtWord, int endAtWord) {
		// FIXME: use fieldName
		Hit hit = new Hit(docId, startAtWord, endAtWord);
		Hits hits = new Hits(this, Arrays.asList(hit));
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
	 * @param startAtWord where to start getting the content (-1 for start of document, 0 for first word)
	 * @param endAtWord where to end getting the content (-1 for end of document)
	 * @return the field content
	 */
	public String getContent(int docId, String fieldName, int startAtWord, int endAtWord) {
		Document d = document(docId);
		ContentAccessor ca = contentAccessors.get(fieldName);
		if (ca == null) {
			// No special content accessor set; assume a stored field
			String content = d.get(fieldName);
			if (content == null)
				throw new RuntimeException("Field not found: " + fieldName);
			return getWordsFromString(content, startAtWord, endAtWord);
		}

		int[] startEnd = startEndWordToCharPos(docId, fieldName, startAtWord,
				endAtWord);
		return ca.getSubstringFromDocument(d, startEnd[0], startEnd[1]);
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
		ContentAccessor ca = contentAccessors.get(fieldName);
		if (ca == null) {
			// No special content accessor set; assume a stored field
			return d.get(fieldName).substring(startAtChar, endAtChar);
		}

		return ca.getSubstringFromDocument(d, startAtChar, endAtChar);
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
	private static String getWordsFromString(String content, int startAtWord,
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
	 * @deprecated use version that takes a docId
	 */
	@Deprecated
	public String getContent(Document d) {
		return getContent(d, mainContentsFieldName);
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
	public DirectoryReader getIndexReader() {
		return reader;
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
		hl.setUnbalancedTagsStrategy(defaultUnbalancedTagsStrategy);
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
		return highlightContent(docId, mainContentsFieldName, hits, -1, -1);
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
	 * Get the content store for a field name.
	 *
	 * @param fieldName the field name
	 * @return the content store, or null if there is no content store for this field
	 */
	public ContentStore getContentStore(String fieldName) {
		ContentAccessor ca = contentAccessors.get(fieldName);
		if (indexMode && ca == null) {
			// Index mode. Create new content store.
			ContentStore contentStore = new ContentStoreDirFixedBlock(new File(indexLocation, "cs_"
					+ fieldName), isEmptyIndex);
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

		if (!indexMode) {
			// Start a background thread to build term indices
			buildTermIndicesThread = new Thread(new Runnable() {
				@Override
				public void run() {
					buildAllTermIndices(); // speed up first call to Terms.indexOf()
				}
			});
			buildTermIndicesThread.start();
		}
	}

	/**
	 * Builds index for Terms.indexOf() method.
	 *
	 * This makes sure the first call to Terms.indexOf() in search mode will be fast.
	 * Subsequent calls are always fast. (Terms.indexOf() is only used in search mode
	 * by HitPropValue.deserialize(), so if you're not sure if you need to call this
	 * method in your application, you probably don't.
	 *
	 * This used to be public, but it's called automatically now in search mode, so
	 * there's no need to call it manually anymore.
	 */
	void buildAllTermIndices() {
		for (Map.Entry<String, ForwardIndex> e: forwardIndices.entrySet()) {
			e.getValue().getTerms().buildTermIndex();
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
			if (!isEmptyIndex && fieldPropName.equals(mainContentsFieldName) && !dir.exists()) {
				// Default forward index used to be called "forward". Look for that instead.
				File alt = new File(indexLocation, "forward");
				if (alt.exists())
					dir = alt;
			}

			if (!isEmptyIndex && !dir.exists()) {
				// Forward index doesn't exist
				return null;
			}
			// Open forward index
			forwardIndex = ForwardIndex.open(dir, indexMode, collator, isEmptyIndex);
			forwardIndex.setIdTranslateInfo(reader, fieldPropName); // how to translate from
																			// Lucene
																			// doc to fiid
			forwardIndices.put(fieldPropName, forwardIndex);
		}
		return forwardIndex;
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
	List<Concordance> makeConcordancesFromContentStore(int doc, String fieldName, int[] startsOfWords,
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
	 * @deprecated renamed to setConcordanceXmlProperties
	 */
	@Deprecated
	public void setForwardIndexConcordanceParameters(String wordFI, String punctFI,
			Collection<String> attrFI) {
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
	public void setConcordanceXmlProperties(String wordFI, String punctFI,
			Collection<String> attrFI) {
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
			type = "fixedblock";
		else {
			VersionFile vf = ContentStoreDirAbstract.getStoreTypeVersion(indexXmlDir);
			type = vf.getType();
		}
		if (type.equals("fixedblock"))
			return new ContentStoreDirFixedBlock(indexXmlDir, create);
		if (type.equals("utf8zip"))
			return new ContentStoreDirZip(indexXmlDir, create);
		if (type.equals("utf8"))
			return new ContentStoreDirUtf8(indexXmlDir, create);
		if (type.equals("utf16")) {
			throw new RuntimeException("UTF-16 content store is deprecated. Please re-index your data.");
		}
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
		return getTerms(ComplexFieldUtil.mainPropertyField(indexStructure, mainContentsFieldName));
	}

	public String getContentsFieldMainPropName() {
		return mainContentsFieldName;
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
		return new QueryExecutionContext(this, fieldName, mainPropName, defaultCaseSensitive,
				defaultDiacriticsSensitive);
	}

	/**
	 * Get the default initial query execution context.
	 *
	 * Uses the default contents field.
	 *
	 * @return the query execution context
	 */
	public QueryExecutionContext getDefaultExecutionContext() {
		return getDefaultExecutionContext(mainContentsFieldName);
	}

	public String getIndexName() {
		return indexLocation.toString();
	}

	public IndexWriter openIndexWriter(File indexDir, boolean create, Analyzer analyzer) throws IOException,
			CorruptIndexException, LockObtainFailedException {
		if (!indexDir.exists() && create) {
			indexDir.mkdir();
		}
		Path indexPath = indexDir.toPath();
		while (Files.isSymbolicLink(indexPath)) {
			// Resolve symlinks, as FSDirectory.open() can't handle them
			indexPath = Files.readSymbolicLink(indexPath);
		}
		Directory indexLuceneDir = FSDirectory.open(indexPath);
		Analyzer defaultAnalyzer = analyzer == null ? new BLDutchAnalyzer() : analyzer;
		IndexWriterConfig config = Utilities.getIndexWriterConfig(defaultAnalyzer, create);
		IndexWriter writer = new IndexWriter(indexLuceneDir, config);

		if (create)
			VersionFile.write(indexDir, "blacklab", "2");
		else {
			if (!isIndex(indexDir)) {
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
			DirectoryReader reader = DirectoryReader.open(indexWriter, false);
			try {
				// Execute the query, iterate over the docs and delete from FI and CS.
				IndexSearcher s = new IndexSearcher(reader);
				Weight w = s.createNormalizedWeight(q, false);
				LeafReader scrw = SlowCompositeReaderWrapper.wrap(reader);
				try {
					Scorer sc = w.scorer(scrw.getContext(), MultiFields.getLiveDocs(reader));
					if (sc == null)
						return; // no matching documents

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
							ContentStore cs = ((ContentAccessorContentStore) ca).getContentStore();
							int cid = Integer.parseInt(d.get(ComplexFieldUtil
									.contentIdField((fieldName))));
							cs.delete(cid);
						}
					}
				} finally {
					scrw.close();
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

	/**
	 * Get the analyzer for indexing and searching.
	 * @return the analyzer
	 */
	public Analyzer getAnalyzer() {
		return analyzer;
	}

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
	@SuppressWarnings("deprecation") // DocResults constructor will be made package-private eventually
	public DocResults queryDocuments(Query documentFilterQuery) {
		return new DocResults(this, documentFilterQuery);
	}

	/**
	 * Determine the term frequencies in a set of documents (defined by the filter query)
	 *
	 * @param documentFilterQuery what set of documents to get the term frequencies for
	 * @param fieldName complex field name, i.e. contents
	 * @param propName property name, i.e. word, lemma, pos, etc.
	 * @param altName alternative name, i.e. s, i (case-sensitivity)
	 * @return the term frequency map
	 */
	public Map<String, Integer> termFrequencies(Query documentFilterQuery, String fieldName, String propName, String altName) {
		try {
			String luceneField = ComplexFieldUtil.propertyField(fieldName, propName, altName);
			Weight weight = indexSearcher.createNormalizedWeight(documentFilterQuery, false);
			Map<String, Integer> freq = new HashMap<>();
			for (LeafReaderContext arc: reader.leaves()) {
				if (weight == null)
					throw new RuntimeException("weight == null");
				if (arc == null)
					throw new RuntimeException("arc == null");
				if (arc.reader() == null)
					throw new RuntimeException("arc.reader() == null");
				Scorer scorer = weight.scorer(arc, arc.reader().getLiveDocs());
				if (scorer != null) {
					while (scorer.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
						LuceneUtil.getFrequenciesFromTermVector(reader, scorer.docID() + arc.docBase, luceneField, freq);
					}
				}
			}
			return freq;
		} catch (IOException e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	/**
	 * Perform a document query and collect the results through a Collector.
	 * @param query query to execute
	 * @param collector object that receives each document hit
	 */
	public void collectDocuments(Query query, Collector collector) {
		try {
			indexSearcher.search(query, collector);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Return the list of terms that occur in a field.
	 *
	 * @param fieldName the field
	 * @param maxResults maximum number to return (or -1 for no limit)
	 * @return the matching terms
	 */
	public List<String> getFieldTerms(String fieldName, int maxResults) {
		try {
			LeafReader srw = SlowCompositeReaderWrapper.wrap(reader);
			return LuceneUtil.getFieldTerms(srw, fieldName, maxResults);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
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
	 * @param analyzerName the classname, optionally preceded by the package name
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

	public String getMainContentsFieldName() {
		return mainContentsFieldName;
	}

	public String getConcWordFI() {
		return concWordFI;
	}

	public String getConcPunctFI() {
		return concPunctFI;
	}

	public Collection<String> getConcAttrFI() {
		return concAttrFI;
	}

	public Map<String, ForwardIndex> getForwardIndices() {
		return forwardIndices;
	}

	public IndexSearcher getIndexSearcher() {
		return indexSearcher;
	}


}
