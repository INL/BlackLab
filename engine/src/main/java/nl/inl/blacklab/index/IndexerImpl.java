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
package nl.inl.blacklab.index;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.mozilla.universalchardet.UniversalDetector;

import net.jcip.annotations.NotThreadSafe;
import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.DocumentFormatNotFound;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.PluginException;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.index.DocIndexerFactory.Format;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLab;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.ContentAccessor;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.util.FileProcessor;
import nl.inl.util.FileUtil;
import nl.inl.util.UnicodeStream;

/**
 * Tool for indexing. Reports its progress to an IndexListener.
 *
 * Not thread-safe, although indexing itself can use thread in certain cases
 * (only when using configuration file based indexing right now)
 */
@NotThreadSafe // in index mode
class IndexerImpl implements DocWriter, Indexer {

    static final Logger logger = LogManager.getLogger(IndexerImpl.class);

    /**
     * FileProcessor FileHandler that creates a DocIndexer for every file and
     * performs some reporting.
     */
    private class DocIndexerWrapper implements FileProcessor.FileHandler {
        @Override
        public void file(String path, byte[] contents, File file) throws IOException, MalformedInputFile, PluginException {
            // Attempt to detect the encoding of our input, falling back to DEFAULT_INPUT_ENCODING if the stream
            // doesn't contain a a BOM. 
            // There is one gotcha, and that is that if the inputstream contains non-textual data, we pass the
            // default encoding to our DocIndexer
            // This usually isn't an issue, since docIndexers work exclusively with either binary data or text.
            // In the case of binary data docIndexers, they should always ignore the encoding anyway
            // and for text docIndexers, passing a binary file is an error in itself already.
            UniversalDetector det = new UniversalDetector(null);
            det.handleData(contents, 0, Math.min(contents.length, 1048576 /* 1 meg */));
            det.dataEnd();
            Charset cs = DEFAULT_INPUT_ENCODING; 
            try {
                cs = Charset.forName(det.getDetectedCharset());
            } catch (Exception e) { 
                logger.trace("Could not determine charset for input file {}, using default ({})", path,  DEFAULT_INPUT_ENCODING.name()); 
            }
            impl(DocumentFormats.get(IndexerImpl.this.formatIdentifier, IndexerImpl.this, path, contents, cs), path);
        }

        @Override
        public void file(String path, InputStream is, File file) throws IOException, MalformedInputFile, PluginException {
            // Attempt to detect the encoding of our inputStream, falling back to DEFAULT_INPUT_ENCODING if the stream
            // doesn't contain a a BOM This doesn't do any character parsing/decoding itself, it just detects and skips
            // the BOM (if present) and exposes the correct character set for this stream (if present)
            // This way we can later use the charset to decode the input
            // There is one gotcha however, and that is that if the inputstream contains non-textual data, we pass the
            // default encoding to our DocIndexer
            // This usually isn't an issue, since docIndexers work exclusively with either binary data or text.
            // In the case of binary data docIndexers, they should always ignore the encoding anyway
            // and for text docIndexers, passing a binary file is an error in itself already.
            try (
                    UnicodeStream inputStream = new UnicodeStream(is, DEFAULT_INPUT_ENCODING);
                    DocIndexer docIndexer = DocumentFormats.get(IndexerImpl.this.formatIdentifier, IndexerImpl.this, path,
                            inputStream, inputStream.getEncoding());) {
                impl(docIndexer, path);
            }
        }

        private void impl(DocIndexer indexer, String documentName) throws MalformedInputFile, PluginException, IOException {
            if (!indexer.continueIndexing())
                return;

            listener().fileStarted(documentName);
            int docsDoneBefore = indexWriter.writer().numDocs();
            long tokensDoneBefore = listener().getTokensProcessed();

            indexer.index();
            listener().fileDone(documentName);
            
            // FIXME the following checks are broken in multithreaded indexing, as the listener is shared between threads
            // So a docIndexer that didn't index anything can slip through if another thread did index some data in the
            // meantime
            int docsDoneAfter = indexWriter.writer().numDocs();
            if (docsDoneAfter == docsDoneBefore) {
                logger.warn("No docs found in " + documentName + "; wrong format?");
            }
            long tokensDoneAfter = listener().getTokensProcessed();
            if (tokensDoneAfter == tokensDoneBefore) {
                logger.warn("No words indexed in " + documentName + "; wrong format?");
            }
        }

        @Override
        public void directory(File dir) {
            // ignore
        }
    }

    private DocIndexerWrapper docIndexerWrapper = new DocIndexerWrapper();

    /** Our index */
    private BlackLabIndexWriter indexWriter;

    /** Stop after indexing this number of docs. -1 if we shouldn't stop. */
    private int maxNumberOfDocsToIndex = -1;

    /** Should we terminate indexing? (e.g. because of an error) */
    private boolean terminateIndexing = false;

    /**
     * Where to report indexing progress.
     */
    private IndexListener listener = null;

    /**
     * Have we reported our creation and the start of indexing to the listener yet?
     */
    private boolean createAndIndexStartReported = false;

    /**
     * When we encounter a zip or tgz file, do we descend into it like it was a
     * directory?
     */
    private boolean processArchivesAsDirectories = true;

    /**
     * Recursively index files inside a directory? (or archive file, if
     * processArchivesAsDirectories == true)
     */
    private boolean defaultRecurseSubdirs = true;

    /**
     * Format of the documents we're going to be indexing, used to create the
     * correct type of DocIndexer.
     */
    private String formatIdentifier;

    /**
     * Parameters we should pass to our DocIndexers upon instantiation.
     */
    private Map<String, String> indexerParam;

    /** How to index metadata fields (tokenized) */
    private FieldType metadataFieldTypeTokenized;

    /** How to index metadata fields (untokenized) */
    private FieldType metadataFieldTypeUntokenized;

    /** Where to look for files linked from the input files */
    private List<File> linkedFileDirs = new ArrayList<>();

    /**
     * If a file cannot be found in the linkedFileDirs, use this to retrieve it (if
     * present)
     */
    private Function<String, File> linkedFileResolver;

    /** Index using multiple threads or just one? */
    private int numberOfThreadsToUse = 1;

    // TODO this is a workaround for a bug where indexMetadata is always written, even when an indexing task was
    // rollbacked on an empty index. Result of this is that the index can never be opened again (the forwardindex
    // is missing files that the indexMetadata.yaml says must exist?) so record rollbacks and then don't write
    // the updated indexMetadata
    private boolean hasRollback = false;

    /** Was this Indexer closed? */
    private boolean closed = false;

    /**
     * Construct Indexer
     *
     * @param directory the main BlackLab index directory
     * @param create if true, creates a new index; otherwise, appends to existing
     *            index
     * @throws DocumentFormatNotFound if autodetection of the document format
     *             failed
     * @throws ErrorOpeningIndex if we couldn't open the index
     */
    IndexerImpl(File directory, boolean create)
            throws DocumentFormatNotFound, ErrorOpeningIndex {
        this(directory, create, (String) null, null);
    }

    /**
     * Construct Indexer
     *
     * @param directory the main BlackLab index directory
     * @param create if true, creates a new index; otherwise, appends to existing
     *            index. When creating a new index, a formatIdentifier or an
     *            indexTemplateFile containing a valid "documentFormat" value should
     *            also be supplied. Otherwise adding new data to the index isn't
     *            possible, as we can't construct a DocIndexer to do the actual
     *            indexing without a valid formatIdentifier.
     * @param formatIdentifier (optional) determines how this Indexer will index any
     *            new data added to it. If omitted, when opening an existing index,
     *            the formatIdentifier in its metadata (as "documentFormat") is used
     *            instead. When creating a new index, this format will be stored as
     *            the default for that index, unless another default is already set
     *            by the indexTemplateFile (as "documentFormat"), it will still be
     *            used by this Indexer however.
     * @param indexTemplateFile JSON file to use as template for index structure /
     *            metadata (if creating new index)
     * @throws DocumentFormatNotFound if no formatIdentifier was specified and
     *             autodetection failed
     * @throws IOException
     * @throws ErrorOpeningIndex
     */
    IndexerImpl(File directory, boolean create, String formatIdentifier, File indexTemplateFile)
            throws DocumentFormatNotFound, ErrorOpeningIndex {
        init(directory, create, formatIdentifier, indexTemplateFile);
    }
    
    /**
     * Open an indexer for the provided writer.
     *
     * @param writer the writer
     * @param formatIdentifier (optional) - the formatIdentifier to use when indexing data through this indexer.
     *      If omitted, uses the default formatIdentifier stored in the indexMetadata. If that is missing too, throws DocumentFormatNotFound.
     */
    IndexerImpl(BlackLabIndexWriter writer, String formatIdentifier) throws DocumentFormatNotFound {
        if (writer == null) {
            throw new BlackLabRuntimeException("writer == null");
        }

        this.indexWriter = writer;

        if (!DocumentFormats.isSupported(formatIdentifier)) {
            formatIdentifier = writer.metadata().documentFormat();
            if (!DocumentFormats.isSupported(formatIdentifier)) {
                String message = formatIdentifier == null ? "No formatIdentifier"
                        : "Unknown formatIdentifier '" + formatIdentifier + "'";
                throw new DocumentFormatNotFound(
                        message + ", and could not determine the default documentFormat for index " + writer.name());
            }
        }
        this.formatIdentifier = formatIdentifier;

        // TODO
        metadataFieldTypeTokenized = new FieldType();
        metadataFieldTypeTokenized.setStored(true);
        //metadataFieldTypeTokenized.setIndexed(true);
        metadataFieldTypeTokenized.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        metadataFieldTypeTokenized.setTokenized(true);
        metadataFieldTypeTokenized.setOmitNorms(true); // <-- depending on setting?
        metadataFieldTypeTokenized.setStoreTermVectors(true);
        metadataFieldTypeTokenized.setStoreTermVectorPositions(true);
        metadataFieldTypeTokenized.setStoreTermVectorOffsets(true);
        metadataFieldTypeTokenized.freeze();

        metadataFieldTypeUntokenized = new FieldType(metadataFieldTypeTokenized);
        metadataFieldTypeUntokenized.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
        //metadataFieldTypeUntokenized.setTokenized(false);  // <-- this should be done with KeywordAnalyzer, otherwise untokenized fields aren't lowercased
        metadataFieldTypeUntokenized.setStoreTermVectors(false);
        metadataFieldTypeUntokenized.setStoreTermVectorPositions(false);
        metadataFieldTypeUntokenized.setStoreTermVectorOffsets(false);
        metadataFieldTypeUntokenized.freeze();
    }

    protected void init(File directory, boolean create, String formatIdentifier, File indexTemplateFile)
            throws DocumentFormatNotFound, ErrorOpeningIndex {

        if (create) {
            if (indexTemplateFile != null) {
                indexWriter = BlackLab.openForWriting(directory, true, indexTemplateFile);

                // Read back the formatIdentifier that was provided through the indexTemplateFile now that the index
                // has written it (might be null)
                final String defaultFormatIdentifier = indexWriter.metadata().documentFormat();

                if (DocumentFormats.isSupported(formatIdentifier)) {
                    this.formatIdentifier = formatIdentifier;
                    if (defaultFormatIdentifier == null || defaultFormatIdentifier.isEmpty()) {
                        // indexTemplateFile didn't provide a default formatIdentifier,
                        // overwrite it with our provided formatIdentifier
                        indexWriter.metadata().setDocumentFormat(formatIdentifier);
                        indexWriter.metadata().save();
                    }
                } else if (DocumentFormats.isSupported(defaultFormatIdentifier)) {
                    this.formatIdentifier = defaultFormatIdentifier;
                } else {
                    // TODO we should delete the newly created index here as it failed, how do we clean up files properly?
                    indexWriter.close();
                    String formatError = DocumentFormats.formatError(formatIdentifier);
                    if (formatError == null)
                        formatError = "format not found";
                    throw new DocumentFormatNotFound("Cannot create new index in " + directory + " with format " + formatIdentifier + ": " +
                            formatError);
//                    throw new DocumentFormatNotFound("Input format config '" + formatIdentifier
//                            + "' not found (or format config contains an error) when creating new index in "
//                            + directory);
                }
            } else if (DocumentFormats.isSupported(formatIdentifier)) {
                this.formatIdentifier = formatIdentifier;

                // No indexTemplateFile, but maybe the formatIdentifier is backed by a ConfigInputFormat (instead of some other DocIndexer implementation)
                // this ConfigInputFormat could then still be used as a minimal template to setup the index
                // (if there's no ConfigInputFormat, that's okay too, a default index template will be used instead)
                ConfigInputFormat format = null;
                for (Format desc : DocumentFormats.getFormats()) {
                    if (desc.getId().equals(formatIdentifier) && desc.getConfig() != null) {
                        format = desc.getConfig();
                        break;
                    }
                }

                // template might still be null, in that case a default will be created
                indexWriter = BlackLab.create(directory, format);

                String defaultFormatIdentifier = indexWriter.metadata().documentFormat();
                if (defaultFormatIdentifier == null || defaultFormatIdentifier.isEmpty()) {
                    // ConfigInputFormat didn't provide a default formatIdentifier,
                    // overwrite it with our provided formatIdentifier
                    indexWriter.metadata().setDocumentFormat(formatIdentifier);
                    indexWriter.metadata().save();
                }
            } else {
                String formatError = DocumentFormats.formatError(formatIdentifier);
                if (formatError == null)
                    formatError = "format not found";
                throw new DocumentFormatNotFound("Cannot create new index in " + directory + " with format " + formatIdentifier + ": " +
                        formatError);
//                throw new DocumentFormatNotFound("Input format config '" + formatIdentifier
//                        + "' not found (or format config contains an error) when creating new index in " + directory);
            }
        } else { // opening an existing index

            this.indexWriter = BlackLab.openForWriting(directory, false);
            String defaultFormatIdentifier = this.indexWriter.metadata().documentFormat();

            if (DocumentFormats.isSupported(formatIdentifier))
                this.formatIdentifier = formatIdentifier;
            else if (DocumentFormats.isSupported(defaultFormatIdentifier))
                this.formatIdentifier = defaultFormatIdentifier;
            else {
                indexWriter.close();
                String message = formatIdentifier == null ? "No formatIdentifier"
                        : "Unknown formatIdentifier '" + formatIdentifier + "'";
                throw new DocumentFormatNotFound(
                        message + ", and could not determine the default documentFormat for index " + directory);
            }
        }

        metadataFieldTypeTokenized = new FieldType();
        metadataFieldTypeTokenized.setStored(true);
        //metadataFieldTypeTokenized.setIndexed(true);
        metadataFieldTypeTokenized.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        metadataFieldTypeTokenized.setTokenized(true);
        metadataFieldTypeTokenized.setOmitNorms(true); // <-- depending on setting?
        metadataFieldTypeTokenized.setStoreTermVectors(true);
        metadataFieldTypeTokenized.setStoreTermVectorPositions(true);
        metadataFieldTypeTokenized.setStoreTermVectorOffsets(true);
        metadataFieldTypeTokenized.freeze();

        metadataFieldTypeUntokenized = new FieldType(metadataFieldTypeTokenized);
        metadataFieldTypeUntokenized.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
        //metadataFieldTypeUntokenized.setTokenized(false);  // <-- this should be done with KeywordAnalyzer, otherwise untokenized fields aren't lowercased
        metadataFieldTypeUntokenized.setStoreTermVectors(false);
        metadataFieldTypeUntokenized.setStoreTermVectorPositions(false);
        metadataFieldTypeUntokenized.setStoreTermVectorOffsets(false);
        metadataFieldTypeUntokenized.freeze();
    }

    @Override
    public FieldType metadataFieldType(boolean tokenized) {
        return tokenized ? metadataFieldTypeTokenized : metadataFieldTypeUntokenized;
    }

    @Override
    public void setProcessArchivesAsDirectories(boolean b) {
        processArchivesAsDirectories = b;
    }

    @Override
    public void setRecurseSubdirs(boolean recurseSubdirs) {
        this.defaultRecurseSubdirs = recurseSubdirs;
    }

    @Override
    public void setFormatIdentifier(String formatIdentifier) throws DocumentFormatNotFound {
        if (!DocumentFormats.isSupported(formatIdentifier))
            throw new DocumentFormatNotFound("Cannot set formatIdentifier '" + formatIdentifier + "' for index "
                    + this.indexWriter.name() + "; unknown identifier");

        this.formatIdentifier = formatIdentifier;
    }

    @Override
    public synchronized void setListener(IndexListener listener) {
        this.listener = listener;
        listener(); // report creation and start of indexing, if it hadn't been reported yet
    }

    @Override
    public synchronized IndexListener listener() {
        if (listener == null) {
            listener = new IndexListenerReportConsole();
        }
        if (!createAndIndexStartReported) {
            createAndIndexStartReported = true;
            listener.indexerCreated(this);
            listener.indexStart();
        }
        return listener;
    }

    /**
     * Log an exception that occurred during indexing
     *
     * @param msg log message
     * @param e the exception
     */
    protected void log(String msg, Exception e) {
        logger.error(msg, e);
    }

    @Override
    public void setMaxNumberOfDocsToIndex(int n) {
        this.maxNumberOfDocsToIndex = n;
    }

    @Override
    public void rollback() {
        listener().rollbackStart();
        indexWriter.rollback();
        listener().rollbackEnd();
        hasRollback = true;
    }

    // TODO this should call close() on running FileProcessors
    @Override
    public synchronized void close() {

        // Signal to the listener that we're done indexing and closing the index (which might take a
        // while)
        listener().indexEnd();
        listener().closeStart();

        if (!hasRollback) {
            indexWriter.metadata().addToTokenCount(listener().getTokensProcessed());
            indexWriter.metadata().save();
        }
        indexWriter.close();

        // Signal that we're completely done now
        listener().closeEnd();
        listener().indexerClosed();

        closed = true;
    }

    @Override
    @Deprecated
    public boolean isClosed() {
        return !isOpen();
    }

    @Override
    public boolean isOpen() {
        return !closed && indexWriter.isOpen();
    }

    /**
     * Add a Lucene document to the index
     *
     * @param document the document to add
     * @throws IOException
     */
    @Override
    public void add(Document document) throws IOException {
        indexWriter.writer().addDocument(document);
        listener().luceneDocumentAdded();
    }

    @Override
    public void update(Term term, Document document) throws IOException {
        indexWriter.writer().updateDocument(term, document);
        listener().luceneDocumentAdded();
    }

    /**
     * Add a list of tokens to an annotation forward index
     *
     * @param prop the annotation to get values and position increments from
     * @return the id assigned to the content
     * @deprecated add a whole field at a time using {@link #addToForwardIndex(AnnotatedFieldWriter, Document)}
     */
    @Override
    @Deprecated
    public int addToForwardIndex(AnnotationWriter prop) {
        Annotation annotation = indexWriter.getOrCreateAnnotation(prop.field(), prop.name());
        AnnotationForwardIndex forwardIndex = indexWriter.annotationForwardIndex(annotation);
        if (forwardIndex == null)
            throw new IllegalArgumentException("No forward index for field " + AnnotatedFieldNameUtil.annotationField(prop.field().name(), prop.name()));
        return forwardIndex.addDocument(prop.values(), prop.positionIncrements());
    }

    @Override
    public void addToForwardIndex(AnnotatedFieldWriter fieldWriter, Document currentLuceneDoc) {
        Map<Annotation, List<String>> annotations = new HashMap<>();
        Map<Annotation, List<Integer>> posIncr = new HashMap<>();
        for (AnnotationWriter annotationWriter: fieldWriter.annotationWriters()) {
            if (annotationWriter.hasForwardIndex()) {
                Annotation annotation = annotationWriter.annotation();
                annotations.put(annotation, annotationWriter.values());
                posIncr.put(annotation, annotationWriter.positionIncrements());
            }
        }
        indexWriter().forwardIndex(fieldWriter.field()).addDocument(annotations, posIncr, currentLuceneDoc);
    }

    @Override
    public void index(String documentName, InputStream input) {
        index(documentName, input, null);
    }

    @Deprecated
    @Override
    public void index(String documentName, Reader reader) {
    	try {
    		index(documentName, IOUtils.toByteArray(reader, DEFAULT_INPUT_ENCODING), null); // convert to our default encoding
    	} catch (IOException e) {
    	    listener().errorOccurred(e, documentName, null);
    	}
    }

    @Override
    public void index(String fileName, InputStream input, String fileNameGlob) {
        try (FileProcessor proc = new FileProcessor(numberOfThreadsToUse, defaultRecurseSubdirs,
                this.processArchivesAsDirectories)) {
            proc.setFileNameGlob(fileNameGlob);
            proc.setFileHandler(docIndexerWrapper);
            proc.setErrorHandler(listener());
            proc.processInputStream(fileName, input, null);
        }
    }

    @Override
    public void index(File file) {
        index(file, "*");
    }

    @Override
    public void index(File file, String fileNameGlob) {
        Optional<String> optGlob = Optional.ofNullable(fileNameGlob);
        try (FileProcessor proc = new FileProcessor(numberOfThreadsToUse, defaultRecurseSubdirs, processArchivesAsDirectories)) {
            proc.setFileNameGlob(optGlob.orElse("*"));
            proc.setFileHandler(docIndexerWrapper);
            proc.setErrorHandler(listener());
            proc.processFile(file);
        }
    }
    
    @Override
    public void index(String fileName, byte[] contents, String fileNameGlob) {
        Optional<String> optGlob = Optional.ofNullable(fileNameGlob);
        try (FileProcessor proc = new FileProcessor(numberOfThreadsToUse, defaultRecurseSubdirs, processArchivesAsDirectories)) {
            proc.setFileNameGlob(optGlob.orElse("*"));
            proc.setFileHandler(docIndexerWrapper);
            proc.setErrorHandler(listener());
            proc.processFile(fileName, contents, null);
        }
    }
    
    /**
     * Should we continue indexing or stop?
     *
     * We stop if we've reached the maximum that was set (if any), or if a fatal
     * error has occurred (indicated by terminateIndexing).
     *
     * @return true if we should continue, false if not
     */
    @Override
    public synchronized boolean continueIndexing() {
        if (!indexWriter.isOpen())
            return false;
        if (terminateIndexing)
            return false;
        if (maxNumberOfDocsToIndex >= 0) {
            return docsToDoLeft() > 0;
        }
        return true;
    }

    /**
     * How many more documents should we process?
     *
     * @return the number of documents
     */
    @Override
    public synchronized int docsToDoLeft() {
        if (maxNumberOfDocsToIndex < 0)
            return maxNumberOfDocsToIndex;
        int docsDone = indexWriter.writer().numDocs();
        return Math.max(0, maxNumberOfDocsToIndex - docsDone);
    }

    /*
     * BlackLab index version history:
     * 1. Initial version
     * 2. Sort index added to forward index; multiple forward indexes possible
     */

    @Override
    public ContentStore contentStore(String fieldName) {
        ContentAccessor contentAccessor = indexWriter.contentAccessor(indexWriter.field(fieldName));
        return contentAccessor == null ? null : contentAccessor.getContentStore();
    }

    @Override
    public File indexLocation() {
        return indexWriter.indexDirectory();
    }

    @Override
    public void setIndexerParam(Map<String, String> indexerParam) {
        this.indexerParam = indexerParam;
    }

    /**
     * Get the parameters we would like to be passed to the DocIndexer class.
     *
     * Used by DocIndexer classes to get their parameters.
     *
     * @return the parameters
     */
    @Override
    public Map<String, String> indexerParameters() {
        return indexerParam;
    }

    /**
     * Get the IndexWriter we're using.
     *
     * Useful if e.g. you want to access FSDirectory.
     *
     * @return the IndexWriter
     */
    protected IndexWriter writer() {
        return indexWriter.writer();
    }

    @Override
    public BlackLabIndexWriter indexWriter() {
        return indexWriter;
    }

    @Override
    public void setLinkedFileDirs(List<File> linkedFileDirs) {
        this.linkedFileDirs.clear();
        this.linkedFileDirs.addAll(linkedFileDirs);
    }

    /**
     * Add a directory to search for linked files.
     *
     * DocIndexerXPath allows us to index a second file into the same Lucene
     * document, which is useful for external metadata, etc. This determines how
     * linked files are located.
     *
     * @param linkedFileDir directory to search
     */
    public void addLinkedFileDir(File linkedFileDir) {
        this.linkedFileDirs.add(linkedFileDir);
    }

    @Override
    public void setLinkedFileResolver(Function<String, File> resolver) {
        this.linkedFileResolver = resolver;
    }

    @Override
    public Optional<Function<String, File>> linkedFileResolver() {
        return Optional.of(this.linkedFileResolver);
    }

    @Override
    public File linkedFile(String inputFile) {
        File f = new File(inputFile);
        if (f.exists())
            return f; // either absolute or relative to current dir
        if (f.isAbsolute())
            return null; // we tried absolute, but didn't find it

        // Look in the configured directories for the relative path
        f = FileUtil.findFile(linkedFileDirs, inputFile, null);
        if (f == null && this.linkedFileResolver != null)
            f = this.linkedFileResolver.apply(inputFile);

        return f;
    }

    @Override
    @Deprecated
    public void setUseThreads(boolean useThreads) {
        this.setNumberOfThreadsToUse(useThreads ? 2 : 1);
    }

    @Override
    public void setNumberOfThreadsToUse(int numberOfThreadsToUse) {
        this.numberOfThreadsToUse = numberOfThreadsToUse;

        // TODO some of the class-based docIndexers don't support theaded indexing
        if (!DocumentFormats.getFormat(formatIdentifier).isConfigurationBased()) {
            logger.info("Threaded indexing is disabled for format " + formatIdentifier);
            this.numberOfThreadsToUse = 1;
        }
    }
}
