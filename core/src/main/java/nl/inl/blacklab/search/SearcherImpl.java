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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.AbstractSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.analysis.BLDutchAnalyzer;
import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.index.config.ConfigInputFormat;
import nl.inl.blacklab.search.indexstructure.ComplexFieldDesc;
import nl.inl.blacklab.search.indexstructure.FieldType;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc;
import nl.inl.blacklab.search.indexstructure.PropertyDesc;
import nl.inl.util.ExUtil;
import nl.inl.util.LuceneUtil;
import nl.inl.util.VersionFile;

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
public class SearcherImpl extends Searcher implements Closeable {

	protected static final Logger logger = LogManager.getLogger(SearcherImpl.class);

	/**
	 * The Lucene index reader
	 */
	IndexReader reader;

	/**
	 * The Lucene IndexSearcher, for dealing with non-Span queries (for per-document scoring)
	 */
	private IndexSearcher indexSearcher;

	/**
	 * Directory where our index resides
	 */
	private File indexLocation;

	/** If true, we've just created a new index. New indices cannot be searched, only added to. */
	private boolean isEmptyIndex = false;

	/** The index writer. Only valid in indexMode. */
	private IndexWriter indexWriter = null;

    /**
     * Open an index.
     *
     * @param indexDir the index directory
     * @param indexMode if true, open in index mode; if false, open in search mode.
     * @param createNewIndex if true, delete existing index in this location if it exists.
     * @param config input format config to use as template for index structure / metadata
     *   (if creating new index)
     * @throws IOException
     */
    SearcherImpl(File indexDir, boolean indexMode, boolean createNewIndex, ConfigInputFormat config)
            throws IOException {
        this.indexMode = indexMode;

        try {
            ConfigReader.applyConfig(this);
        } catch(IllegalArgumentException e) {
            throw new IllegalArgumentException(e.getMessage() + " (BlackLab configuration file)", e.getCause());
        }

        openIndex(indexDir, indexMode, createNewIndex);

        // Determine the index structure
        if (traceIndexOpening) logger.debug("  Determining index structure...");
        indexStructure = new IndexStructure(reader, indexDir, createNewIndex, config);

        finishOpeningIndex(indexDir, indexMode, createNewIndex);
    }

	/**
	 * Open an index.
	 *
	 * @param indexDir the index directory
	 * @param indexMode if true, open in index mode; if false, open in search mode.
	 * @param createNewIndex if true, delete existing index in this location if it exists.
	 * @param indexTemplateFile index template file to use to create index
	 * @throws IOException
	 */
	SearcherImpl(File indexDir, boolean indexMode, boolean createNewIndex, File indexTemplateFile)
			throws IOException {
		this.indexMode = indexMode;

        ConfigReader.applyConfig(this);

		openIndex(indexDir, indexMode, createNewIndex);

		// Determine the index structure
		if (traceIndexOpening) logger.debug("  Determining index structure...");
		indexStructure = new IndexStructure(reader, indexDir, createNewIndex, indexTemplateFile);

		finishOpeningIndex(indexDir, indexMode, createNewIndex);
	}

    protected void openIndex(File indexDir, boolean indexMode, boolean createNewIndex)
            throws IOException, CorruptIndexException, LockObtainFailedException {
        if (!indexMode && createNewIndex)
			throw new RuntimeException("Cannot create new index, not in index mode");

		if (!createNewIndex) {
			if (!indexMode || VersionFile.exists(indexDir)) {
				if (!isIndex(indexDir)) {
					throw new IllegalArgumentException("Not a BlackLab index, or wrong version! "
							+ VersionFile.report(indexDir));
				}
			}
		}

		if (traceIndexOpening) logger.debug("Constructing Searcher...");

		if (indexMode) {
			if (traceIndexOpening) logger.debug("  Opening IndexWriter...");
			indexWriter = openIndexWriter(indexDir, createNewIndex, null);
			if (traceIndexOpening) logger.debug("  Opening corresponding IndexReader...");
			reader = DirectoryReader.open(indexWriter, false);
		} else {
			// Open Lucene index
			if (traceIndexOpening) logger.debug("  Following symlinks...");
			Path indexPath = indexDir.toPath();
			while (Files.isSymbolicLink(indexPath)) {
				// Resolve symlinks, as FSDirectory.open() can't handle them
				indexPath = Files.readSymbolicLink(indexPath);
			}
			if (traceIndexOpening) logger.debug("  Opening IndexReader...");
			reader = DirectoryReader.open(FSDirectory.open(indexPath));
		}
		this.indexLocation = indexDir;

//      logger.debug("TOTAL TERM FREQ contents%lemma@i: " + reader.getSumTotalTermFreq("contents%lemma@i"));
//      logger.debug("TOTAL TERM FREQ test: " + reader.getSumTotalTermFreq("test"));

    }

	protected void finishOpeningIndex(File indexDir, boolean indexMode, boolean createNewIndex)
            throws IOException, CorruptIndexException, LockObtainFailedException {
        isEmptyIndex = indexStructure.isNewIndex();

    	// TODO: we need to create the analyzer before opening the index, because
    	//   we can't change the analyzer attached to the IndexWriter (and passing a different
    	//   analyzer in addDocument() went away in Lucene 5.x).
    	//   For now, if we're in index mode, we re-open the index with the analyzer we determined.
    	if (traceIndexOpening) logger.debug("  Creating analyzers...");
    	createAnalyzers();

    	if (indexMode) {
    		// Re-open the IndexWriter with the analyzer we've created above (see comment above)
    		if (traceIndexOpening) logger.debug("  Re-opening IndexWriter with newly created analyzers...");
    		reader.close();
    		reader = null;
    		indexWriter.close();
    		indexWriter = null;
    		indexWriter = openIndexWriter(indexDir, createNewIndex, analyzer);
    		if (traceIndexOpening) logger.debug("  IndexReader too...");
    		reader = DirectoryReader.open(indexWriter, false);
    	}

    	// Register ourselves in the mapping from IndexReader to Searcher,
    	// so we can find the corresponding Searcher object from within Lucene code
    	searcherFromIndexReader.put(reader, this);

    	// Detect and open the ContentStore for the contents field
    	if (!createNewIndex) {
    		if (traceIndexOpening) logger.debug("  Determining main contents field name...");
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
    				hitsSettings.setConcordanceType(ConcordanceType.FORWARD_INDEX);
    			}
    		}

    		// Register content stores
    		if (traceIndexOpening) logger.debug("  Opening content stores...");
    		for (String cfn: indexStructure.getComplexFields()) {
    			if (indexStructure.getComplexFieldDesc(cfn).hasContentStore()) {
    				File dir = new File(indexDir, "cs_" + cfn);
    				if (!dir.exists()) {
    					dir = new File(indexDir, "xml"); // OLD, should eventually be removed
    				}
    				if (dir.exists()) {
    					if (traceIndexOpening) logger.debug("    " + dir + "...");
    					registerContentStore(cfn, openContentStore(dir, false));
    				}
    			}
    		}
    	}

    	if (traceIndexOpening) logger.debug("  Opening IndexSearcher...");
    	indexSearcher = new IndexSearcher(reader);

    	// Make sure large wildcard/regex expansions succeed
    	if (traceIndexOpening) logger.debug("  Setting maxClauseCount...");
    	BooleanQuery.setMaxClauseCount(100000);

    	// Open the forward indices
    	if (!createNewIndex) {
    		if (traceIndexOpening) logger.debug("  Opening forward indices...");
    		openForwardIndices();
    	}
    }

    @Override
	public boolean isEmpty() {
		return isEmptyIndex;
	}

	private void createAnalyzers() {
		Map<String, Analyzer> fieldAnalyzers = new HashMap<>();
		fieldAnalyzers.put("fromInputFile", getAnalyzerInstance("nontokenizing"));
		Analyzer baseAnalyzer = getAnalyzerInstance(indexStructure.getDefaultAnalyzerName());
		for (String fieldName: indexStructure.getMetadataFields()) {
			MetadataFieldDesc fd = indexStructure.getMetadataFieldDesc(fieldName);
			String analyzerName = fd.getAnalyzerName();
			if (fd.getType() == FieldType.UNTOKENIZED)
			    analyzerName = "nontokenizing";
			if (analyzerName.length() > 0 && !analyzerName.equalsIgnoreCase("default")) {
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

	@Override
	public void rollback() {
		try {
			indexWriter.rollback();
			indexWriter = null;
		} catch (IOException e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	@Override
	public void close() {
		try {
			reader.close();
			if (indexWriter != null) {
				indexWriter.commit();
				indexWriter.close();
			}

			super.close();

			searcherFromIndexReader.remove(reader);

		} catch (IOException e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	@Override
	public Document document(int doc) {
		try {
			if (doc < 0)
				throw new IllegalArgumentException("Negative document id");
			if (doc >= reader.maxDoc())
				throw new IllegalArgumentException("Document id >= maxDoc");
			return reader.document(doc);
		} catch (Exception e) {
			throw ExUtil.wrapRuntimeException(e);
		}
	}

	@Override
	public boolean isDeleted(int doc) {
		Bits liveDocs = MultiFields.getLiveDocs(reader);
		return liveDocs != null && !liveDocs.get(doc);
	}

	@Override
	public int maxDoc() {
		return reader.maxDoc();
	}

	@Override
	public void getCharacterOffsets(int doc, String fieldName, int[] startsOfWords, int[] endsOfWords,
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
				throw new IllegalArgumentException("Field " + fieldPropName + " in doc " + doc + " has no term vector");
			if (!terms.hasPositions())
				throw new IllegalArgumentException("Field " + fieldPropName + " in doc " + doc + " has no character postion information");

			//int lowestPos = -1, highestPos = -1;
			int lowestPosFirstChar = -1, highestPosLastChar = -1;
			int total = numStarts + numEnds;
			boolean[] done = new boolean[total]; // NOTE: array is automatically initialized to zeroes!
			int found = 0;

			// Iterate over terms
			TermsEnum termsEnum = terms.iterator();
			while (termsEnum.next() != null) {
				PostingsEnum dpe = termsEnum.postings(null, PostingsEnum.POSITIONS);

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

	@Override
	public IndexReader getIndexReader() {
		return reader;
	}

	@Override
	protected ContentStore openContentStore(String fieldName) {
		File contentStoreDir = new File(indexLocation, "cs_" + fieldName);
		ContentStore contentStore = ContentStore.open(contentStoreDir, isEmptyIndex);
		registerContentStore(fieldName, contentStore);
		return contentStore;
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
					String fieldProp = ComplexFieldUtil.propertyField(field, property);
					if (traceIndexOpening) logger.debug("    " + fieldProp + "...");
					getForwardIndex(fieldProp);
				}
			}
		}
	}

	@Override
	protected ForwardIndex openForwardIndex(String fieldPropName) {
		ForwardIndex forwardIndex;
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
		forwardIndex = ForwardIndex.open(dir, indexMode, getCollator(), isEmptyIndex);
		forwardIndex.setIdTranslateInfo(reader, fieldPropName); // how to translate from
																		// Lucene
																		// doc to fiid
		return forwardIndex;
	}

	@Override
	public QueryExecutionContext getDefaultExecutionContext(String fieldName) {
		ComplexFieldDesc complexFieldDesc = indexStructure.getComplexFieldDesc(fieldName);
		if (complexFieldDesc == null)
			throw new IllegalArgumentException("Unknown complex field " + fieldName);
		PropertyDesc mainProperty = complexFieldDesc.getMainProperty();
		if (mainProperty == null)
			throw new IllegalArgumentException("Main property not found for " + fieldName);
		String mainPropName = mainProperty.getName();
		return new QueryExecutionContext(this, fieldName, mainPropName, defaultCaseSensitive,
				defaultDiacriticsSensitive);
	}

	@Override
	public String getIndexName() {
		return indexLocation.toString();
	}

	@Override
	public IndexWriter openIndexWriter(File indexDir, boolean create, Analyzer useAnalyzer) throws IOException,
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
		if (useAnalyzer == null)
			useAnalyzer = new BLDutchAnalyzer();
		IndexWriterConfig config = LuceneUtil.getIndexWriterConfig(useAnalyzer, create);
		IndexWriter writer = new IndexWriter(indexLuceneDir, config);

		if (create)
			VersionFile.write(indexDir, "blacklab", "2");
		else {
			if (!isIndex(indexDir)) {
				throw new IllegalArgumentException("Not a BlackLab index, or wrong type or version! "
						+ VersionFile.report(indexDir));
			}
		}

		return writer;
	}

	@Override
	public IndexWriter getWriter() {
		return indexWriter;
	}

	@Override
	public File getIndexDirectory() {
		return indexLocation;
	}

	@Override
	public void delete(Query q) {
		if (!indexMode)
			throw new RuntimeException("Cannot delete documents, not in index mode");
		try {
			// Open a fresh reader to execute the query
			try (IndexReader freshReader = DirectoryReader.open(indexWriter, false)) {
				// Execute the query, iterate over the docs and delete from FI and CS.
				IndexSearcher s = new IndexSearcher(freshReader);
				Weight w = s.createNormalizedWeight(q, false);
				for (LeafReaderContext leafContext: freshReader.leaves()) {
					Scorer scorer = w.scorer(leafContext);
					if (scorer == null)
						return; // no matching documents

					// Iterate over matching docs
					DocIdSetIterator it = scorer.iterator();
					while (true) {
						int docId;
						try {
							docId = it.nextDoc() + leafContext.docBase;
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
						if (docId == DocIdSetIterator.NO_MORE_DOCS)
							break;
						Document d = freshReader.document(docId);

						deleteFromForwardIndices(d);

						// Delete this document in all content stores
						contentStores.deleteDocument(d);
					}
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

	@Override
	public List<String> getFieldTerms(String fieldName, int maxResults) {
		return LuceneUtil.getFieldTerms(reader, fieldName, maxResults);
	}

	@Override
	public IndexSearcher getIndexSearcher() {
		return indexSearcher;
	}

	@Override
	public Set<Integer> docIdSet() {

		final int maxDoc = reader.maxDoc();

		final Bits liveDocs = MultiFields.getLiveDocs(reader);

		return new AbstractSet<Integer>() {
			@Override
			public boolean contains(Object o) {
				Integer i = (Integer)o;
				return i < maxDoc && !isDeleted(i);
			}

			boolean isDeleted(Integer i) {
				return liveDocs != null && !liveDocs.get(i);
			}

			@Override
			public boolean isEmpty() {
				return maxDoc == reader.numDeletedDocs() + 1;
			}

			@Override
			public Iterator<Integer> iterator() {
				return new Iterator<Integer>() {
					int current = -1;
					int next = -1;

					@Override
					public boolean hasNext() {
						if (next < 0)
							findNext();
						return next < maxDoc;
					}

					private void findNext() {
						next = current + 1;
						while (next < maxDoc && isDeleted(next)) {
							next++;
						}
					}

					@Override
					public Integer next() {
						if (next < 0)
							findNext();
						if (next >= maxDoc)
							throw new NoSuchElementException();
						current = next;
						next = -1;
						return current;
					}

					@Override
					public void remove() {
						throw new UnsupportedOperationException();
					}
				};
			}

			@Override
			public int size() {
				return maxDoc - reader.numDeletedDocs() - 1;
			}
		};
	}

}
