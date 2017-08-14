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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;

import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.index.complex.ComplexFieldProperty;
import nl.inl.blacklab.search.Searcher;
import nl.inl.util.ExUtil;
import nl.inl.util.FileProcessor;
import nl.inl.util.FileProcessor.ErrorHandler;
import nl.inl.util.FileProcessor.FileHandler;
import nl.inl.util.FileUtil;
import nl.inl.util.UnicodeStream;

/**
 * Tool for indexing. Reports its progress to an IndexListener.
 */
public class Indexer {

	static final Logger logger = LogManager.getLogger(Indexer.class);

	public static final Charset DEFAULT_INPUT_ENCODING = Charset.forName("utf-8");

    /** File handler that reads a single file into a byte array. */
    static final class FetchFileHandler implements FileHandler {

        protected final String pathToFile;

        byte[] bytes;

        FetchFileHandler(String pathInsideArchive) {
            this.pathToFile = pathInsideArchive;
        }

        @Override
        public void stream(String path, InputStream f) {
            if (path.equals(pathToFile)) {
                try {
                    bytes = IOUtils.toByteArray(f);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void file(String path, File f) {
            throw new UnsupportedOperationException();
        }
    }

    public static byte[] fetchFileFromArchive(File f, final String pathInsideArchive) {
        if (f.getName().endsWith(".gz") || f.getName().endsWith(".tgz")) {
            // We have to process the whole file, we can't do random access.
            FileProcessor proc = new FileProcessor(false, true);
            FetchFileHandler fileHandler = new FetchFileHandler(pathInsideArchive);
            proc.setFileHandler(fileHandler);
            try {
                proc.processFile(f);
                return fileHandler.bytes;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else if (f.getName().endsWith(".zip")) {
            // We can do random access. Fetch the file we want.
            try {
                ZipFile z = ZipHandleManager.openZip(f);
                ZipEntry e = z.getEntry(pathInsideArchive);
                InputStream is = z.getInputStream(e);
                return IOUtils.toByteArray(is);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported archive type: " + f.getName());
        }
    }

	/** Our index */
	protected Searcher searcher;

	/** Stop after indexing this number of docs. -1 if we shouldn't stop. */
	protected int maxNumberOfDocsToIndex = -1;

	/** Should we terminate indexing? (e.g. because of an error) */
	boolean terminateIndexing = false;

	/**
	 * Where to report indexing progress.
	 */
	protected IndexListener listener = null;

	/**
	 * Have we reported our creation and the start of indexing to the listener yet?
	 */
	protected boolean createAndIndexStartReported = false;

	/**
	 * When we encounter a zip or tgz file, do we descend into it like it was a directory?
	 */
	boolean processArchivesAsDirectories = true;

	/**
	 * Recursively index files inside a directory? (or archive file, if processArchivesAsDirectories == true)
	 */
	protected boolean defaultRecurseSubdirs = true;

	/**
	 * How to instantiate DocIndexers for the file format we're indexing.
	 */
    protected DocIndexerFactory docIndexerFactory;

	/** If an error occurs (e.g. an XML parse error), should we
	 *  try to continue indexing, or abort? */
	protected boolean continueAfterInputError = true;

	/** If an error occurs (e.g. an XML parse error), and we don't
	 * continue indexing, should we re-throw it, or assume the client
	 * picked it up in the listener and return normally? */
	protected boolean rethrowInputError = true;

	/**
	 * Parameters we should pass to our DocIndexers upon instantiation.
	 */
	protected Map<String, String> indexerParam;

	/** How to index metadata fields (tokenized) */
	protected FieldType metadataFieldTypeTokenized;

	/** How to index metadata fields (untokenized) */
	protected FieldType metadataFieldTypeUntokenized;

	/** Where to look for files linked from the input files */
    protected List<File> linkedFileDirs = new ArrayList<>();

    public FieldType getMetadataFieldType(boolean tokenized) {
	    return tokenized ? metadataFieldTypeTokenized : metadataFieldTypeUntokenized;
	}

	/** If an error occurs (e.g. an XML parse error), should we
	 *  try to continue indexing, or abort?
	 *  @param b if true, continue; if false, abort
	 */
	public void setContinueAfterInputError(boolean b) {
		continueAfterInputError = b;
	}

	/** If an error occurs (e.g. an XML parse error), and we don't
	 * continue indexing, should we re-throw it, or assume the client
	 * picked it up in the listener and return normally?
	 *  @param b if true, re-throw it; if false, return as normal
	 */
	public void setRethrowInputError(boolean b) {
		rethrowInputError = b;
	}

	/**
	 * When we encounter a zip or tgz file, do we descend into it like it was a directory?
	 *
	 * Note that for accessing large ZIP files, you need Java 7 which supports the
	 * ZIP64 format, otherwise you'll get the "invalid CEN header (bad signature)" error.
	 *
	 * @param b
	 *            if true, treats zipfiles like a directory and processes all the files inside
	 */
	public void setProcessArchivesAsDirectories(boolean b) {
		processArchivesAsDirectories = b;
	}

	/**
	 * Should we recursively index files in subdirectories (and archives files, if that setting is on)?
	 * @param recurseSubdirs true if we should recurse into subdirs
	 */
	public void setRecurseSubdirs(boolean recurseSubdirs) {
		this.defaultRecurseSubdirs = recurseSubdirs;
	}

	/**
	 * Construct Indexer
	 *
	 * @param directory
	 *            the main BlackLab index directory
	 * @param create
	 *            if true, creates a new index; otherwise, appends to existing index
	 * @param docIndexerClass how to index the files, or null to autodetect
	 * @throws IOException
	 * @throws DocumentFormatException if no DocIndexer was specified and autodetection failed
     * @Deprecated use DocIndexerFactory version
	 */
	@Deprecated
	public Indexer(File directory, boolean create, Class<? extends DocIndexer> docIndexerClass)
			throws IOException, DocumentFormatException {
		this(directory, create, docIndexerClass, (File)null);
	}

	/**
	 * Construct Indexer
	 *
	 * @param directory
	 *            the main BlackLab index directory
	 * @param create
	 *            if true, creates a new index; otherwise, appends to existing index
	 * @throws IOException
	 * @throws DocumentFormatException if autodetection of the document format failed
     * @Deprecated use DocIndexerFactory version
	 */
	@Deprecated
	public Indexer(File directory, boolean create)
			throws IOException, DocumentFormatException {
		this(directory, create, (Class<? extends DocIndexer>)null, (File)null);
	}

	/**
	 * Construct Indexer
	 *
	 * @param directory
	 *            the main BlackLab index directory
	 * @param create
	 *            if true, creates a new index; otherwise, appends to existing index
	 * @param docIndexerClass how to index the files, or null to autodetect
	 * @param indexTemplateFile JSON file to use as template for index structure / metadata
	 *   (if creating new index)
	 * @throws DocumentFormatException if no DocIndexer was specified and autodetection failed
	 * @throws IOException
	 * @Deprecated use DocIndexerFactory version
	 */
	@Deprecated
	public Indexer(File directory, boolean create,
			Class<? extends DocIndexer> docIndexerClass, File indexTemplateFile) throws DocumentFormatException, IOException {
		this.docIndexerFactory = new DocIndexerFactoryClass(docIndexerClass);

		init(directory, create, indexTemplateFile);
	}

    /**
     * Construct Indexer
     *
     * @param directory
     *            the main BlackLab index directory
     * @param create
     *            if true, creates a new index; otherwise, appends to existing index
     * @param docIndexerFactory how to index the files, or null to autodetect
     * @param indexTemplateFile JSON file to use as template for index structure / metadata
     *   (if creating new index)
     * @throws DocumentFormatException if no DocIndexer was specified and autodetection failed
     * @throws IOException
     */
    public Indexer(File directory, boolean create, DocIndexerFactory docIndexerFactory, File indexTemplateFile)
            throws DocumentFormatException, IOException {
        this.docIndexerFactory = docIndexerFactory;
        init(directory, create, indexTemplateFile);
    }

    protected void init(File directory, boolean create, File indexTemplateFile) throws IOException, DocumentFormatException {
        if (docIndexerFactory.getConfig() != null && indexTemplateFile == null) {
            // Use the input format definition as the index template
            searcher = Searcher.openForWriting(directory, create, docIndexerFactory.getConfig());
        } else {
            searcher = Searcher.openForWriting(directory, create, indexTemplateFile);
        }
		if (!create)
			searcher.getIndexStructure().setModified();

		if (this.docIndexerFactory == null) {
			// No DocIndexer supplied; try to detect it from the index
			// metadata.
			String formatId = searcher.getIndexStructure().getDocumentFormat();
			if (formatId != null && formatId.length() > 0)
				docIndexerFactory = DocumentFormats.getIndexerFactory(formatId);
			else {
				throw new DocumentFormatException("Cannot detect document format for index!");
			}
		}

		metadataFieldTypeTokenized = new FieldType();
		metadataFieldTypeTokenized.setStored(true);
		//metadataFieldTypeTokenized.setIndexed(true);
		metadataFieldTypeTokenized.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
		metadataFieldTypeTokenized.setTokenized(true);
		metadataFieldTypeTokenized.setOmitNorms(true); // @@@ <-- depending on setting?
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

	public DocIndexerFactory getDocIndexerFactory() {
        return docIndexerFactory;
    }

    public void setDocIndexerFactory(DocIndexerFactory docIndexerFactory) {
        this.docIndexerFactory = docIndexerFactory;
    }

    /**
	 * Set the listener object that receives messages about indexing progress.
	 * @param listener the listener object to report to
	 */
	public void setListener(IndexListener listener) {
		this.listener = listener;
		getListener(); // report creation and start of indexing, if it hadn't been reported yet
	}

	/**
	 * Get our index listener, or create a console reporting listener if none was set yet.
	 *
	 * Also reports the creation of the Indexer and start of indexing, if it hadn't been reported
	 * already.
	 *
	 * @return the listener
	 */
	public IndexListener getListener() {
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
	 * @param msg log message
	 * @param e the exception
	 */
	protected void log(String msg, Exception e) {
		logger.error(msg, e);
	}

	/**
	 * Set number of documents after which we should stop.
	 * Useful when testing.
	 * @param n number of documents after which to stop
	 */
	public void setMaxNumberOfDocsToIndex(int n) {
		this.maxNumberOfDocsToIndex = n;
	}

	/**
	 * Call this to roll back any changes made to the index this session.
	 * Calling close() will automatically commit any changes. If you call this
	 * method, then call close(), no changes will be committed.
	 */
	public void rollback() {
		getListener().rollbackStart();
		searcher.rollback();
		getListener().rollbackEnd();
	}

	/**
	 * Close the index
	 *
	 * @throws IOException
	 * @throws CorruptIndexException
	 */
	public void close() throws CorruptIndexException, IOException {

		// Signal to the listener that we're done indexing and closing the index (which might take a
		// while)
		getListener().indexEnd();
		getListener().closeStart();

		searcher.getIndexStructure().addToTokenCount(getListener().getTokensProcessed());
		searcher.getIndexStructure().writeMetadata();

		searcher.close();

		// Signal that we're completely done now
		getListener().closeEnd();
		getListener().indexerClosed();
	}

	/**
	 * Set the DocIndexer class we should use to index documents.
	 * @param docIndexerClass the class
	 * @deprecated use setDocIndexerFactory
	 */
	@Deprecated
	public void setDocIndexer(Class<? extends DocIndexer> docIndexerClass) {
		this.docIndexerFactory = new DocIndexerFactoryClass(docIndexerClass);
	}

	/**
	 * Add a Lucene document to the index
	 *
	 * @param document
	 *            the document to add
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	public void add(Document document) throws CorruptIndexException, IOException {
		searcher.getWriter().addDocument(document);
		getListener().luceneDocumentAdded();
	}

	/**
	 * Updates the specified Document in the index.
	 *
	 * @param term how to find the document to update
	 * @param document the updated document
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	public void update(Term term, Document document) throws CorruptIndexException, IOException {
		searcher.getWriter().updateDocument(term, document);
		getListener().luceneDocumentAdded();
	}

	/**
	 * Add a list of tokens to a forward index
	 *
	 * @param fieldName what forward index to add this to
	 * @param prop the property to get values and position increments from
	 * @return the id assigned to the content
	 */
	public int addToForwardIndex(String fieldName, ComplexFieldProperty prop) {
		ForwardIndex forwardIndex = searcher.getForwardIndex(fieldName);
		if (forwardIndex == null)
			throw new IllegalArgumentException("No forward index for field " + fieldName);

		return forwardIndex.addDocument(prop.getValues(), prop.getPositionIncrements());
	}

	void indexDocIndexer(String documentName, DocIndexer docIndexer) throws Exception {
        getListener().fileStarted(documentName);
        int docsDoneBefore = searcher.getWriter().numDocs();
        long tokensDoneBefore = getListener().getTokensProcessed();

		docIndexer.index();
		getListener().fileDone(documentName);
		int docsDoneAfter = searcher.getWriter().numDocs();
		if (docsDoneAfter == docsDoneBefore) {
			System.err.println("*** Warning, couldn't index " + documentName + "; wrong format?");
		}
		long tokensDoneAfter = getListener().getTokensProcessed();
		if (tokensDoneAfter == tokensDoneBefore) {
			System.err.println("*** Warning, no words indexed in " + documentName + "; wrong format?");
		}
	}

	/**
     * Index a document from an InputStream.
     *
     * @param documentName
     *            name for the InputStream (e.g. name of the file)
     * @param input
     *            the stream
     * @throws Exception
     */
    public void index(String documentName, InputStream input) throws Exception {
        UnicodeStream is = new UnicodeStream(input, DEFAULT_INPUT_ENCODING);
        DocIndexer docIndexer = docIndexerFactory.get(this, documentName, is, is.getEncoding());
        indexDocIndexer(documentName, docIndexer);
    }

    /**
	 * Index a document from a Reader.
	 *
	 * NOTE: it is generally better to supply an (UTF-8) InputStream or byte array directly,
	 * as this can in some cases be parsed more efficiently (e.g. using VTD-XML).
	 *
	 * Catches and reports any errors that occur.
	 *
	 * @param documentName
	 *            some (preferably unique) name for this document (for example, the file
	 *            name or path)
	 * @param reader
	 *            where to index from
	 * @throws Exception
	 */
	public void index(String documentName, Reader reader) throws Exception {
		try {
			DocIndexer docIndexer = docIndexerFactory.get(this, documentName, reader);
            indexDocIndexer(documentName, docIndexer);
		} catch (InputFormatException e) {
			listener.errorOccurred(e.getMessage(), "reader", new File(documentName), null);
			if (continueAfterInputError) {
				System.err.println("Parsing " + documentName + " failed:");
				e.printStackTrace();
				System.err.println("(continuing indexing)");
			} else {
				// Don't continue; re-throw the exception so we eventually abort
				System.err.println("Input error while processing " + documentName);
				if (rethrowInputError)
					throw e;
				e.printStackTrace();
			}
		} catch (Exception e) {
			listener.errorOccurred(e.getMessage(), "reader", new File(documentName), null);
			if (continueAfterInputError) {
				System.err.println("Parsing " + documentName + " failed:");
				e.printStackTrace();
				System.err.println("(continuing indexing)");
			} else {
				System.err.println("Exception while processing " + documentName);
				if (rethrowInputError)
					throw e;
				e.printStackTrace();
			}
		}
	}

	/**
	 * Index the file or directory specified.

	 * Indexes all files in a directory or archive (previously
	 * only indexed *.xml; specify a glob if you want this
	 * behaviour back, see index(File, String)).
	 *
	 * Recurses into subdirs only if that setting is enabled.
	 *
	 * @param file
	 *            the input file or directory
	 * @throws Exception
	 */
	public void index(File file) throws Exception {
		indexInternal(file, "*", defaultRecurseSubdirs);
	}

	/**
	 * Index a group of files in a directory or archive.
	 *
	 * Recurses into subdirs only if that setting is enabled.
	 *
	 * @param fileToIndex
	 *            directory or archive to index
	 * @param glob what files to index
	 * @throws UnsupportedEncodingException
	 * @throws FileNotFoundException
	 * @throws Exception
	 * @throws IOException
	 */
	public void index(File fileToIndex, String glob)
			throws UnsupportedEncodingException, FileNotFoundException, IOException, Exception {
		indexInternal(fileToIndex, glob, defaultRecurseSubdirs);
	}

	/**
	 * Index a group of files in a directory or archive.
	 *
	 * @param fileToIndex
	 *            directory or archive to index
	 * @param glob what files to index
	 * @param recurseSubdirs whether or not to index subdirectories (overrides the setting)
	 * @throws UnsupportedEncodingException
	 * @throws FileNotFoundException
	 * @throws Exception
	 * @throws IOException
	 */
	protected void indexInternal(File fileToIndex, String glob, boolean recurseSubdirs)
			throws UnsupportedEncodingException, FileNotFoundException, IOException, Exception {
	    FileProcessor proc = new FileProcessor(recurseSubdirs, recurseSubdirs && processArchivesAsDirectories);
	    proc.setFileNameGlob(glob);
        proc.setFileHandler(new FileProcessor.FileHandler() {
            @Override
            public void stream(String path, InputStream is) {
                try {
                    index(path, is);
                } catch (Exception e) {
                    throw ExUtil.wrapRuntimeException(e);
                }
            }

            @Override
            public void file(String path, File f) {
                try {
                    // Regular file.
                    DocIndexer docIndexer = getDocIndexerFactory().get(Indexer.this, path, f, DEFAULT_INPUT_ENCODING);
                    indexDocIndexer(path, docIndexer);
                } catch (Exception e) {
                    throw ExUtil.wrapRuntimeException(e);
                }
            }
        });
	    proc.setErrorHandler(new ErrorHandler() {
            @Override
            public boolean errorOccurred(String file, String msg, Exception e) {
                log("*** Error indexing " + file, e);
                return getListener().errorOccurred(e.getMessage(), "file", new File(file), null);
            }
        });
	    proc.processFile(fileToIndex);
	}

    public void indexInputStream(final String filePath, InputStream inputStream, String glob, boolean processArchives) {
        FileProcessor proc = new FileProcessor(true, processArchivesAsDirectories);
        proc.setFileNameGlob(glob);
        proc.setProcessArchives(processArchives);
        proc.setFileHandler(new FileProcessor.FileHandler() {
            @Override
            public void stream(String path, InputStream is) {
                try {
                    index(path, is);
                } catch (Exception e) {
                    throw ExUtil.wrapRuntimeException(e);
                }
            }

            @Override
            public void file(String path, File f) {
                throw new UnsupportedOperationException();
            }
        });
        proc.setErrorHandler(new ErrorHandler() {
            @Override
            public boolean errorOccurred(String file, String msg, Exception e) {
                log("*** Error indexing " + file, e);
                return getListener().errorOccurred(e.getMessage(), "file", new File(file), null);
            }
        });
        proc.processInputStream(filePath, inputStream);
    }

	@Deprecated
	public void indexGzip(final String gzFileName, InputStream gzipStream) {
        indexInputStream(gzFileName, gzipStream, "*", true);
	}

    @Deprecated
	public void indexTarGzip(final String tgzFileName, InputStream tarGzipStream, final String glob, final boolean recurseArchives) {
        indexInputStream(tgzFileName, tarGzipStream, glob, recurseArchives);
	}

	/**
	 * Should we skip the specified file because it is a special OS file?
	 *
	 * Skips Windows Thumbs.db file and Mac OSX .DS_Store file.
	 *
	 * @param fileName name of the file
	 * @return true if we should skip it, false otherwise
	 */
	protected boolean isSpecialOperatingSystemFile(String fileName) {
		return fileName.equals("Thumbs.db") || fileName.equals(".DS_Store");
	}

	/**
	 * Should we continue indexing or stop?
	 *
	 * We stop if we've reached the maximum that was set (if any),
	 * or if a fatal error has occurred (indicated by terminateIndexing).
	 *
	 * @return true if we should continue, false if not
	 */
	public synchronized boolean continueIndexing() {
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
	public synchronized int docsToDoLeft() {
		try {
			if (maxNumberOfDocsToIndex < 0)
				return maxNumberOfDocsToIndex;
			int docsDone = searcher.getWriter().numDocs();
			return Math.max(0, maxNumberOfDocsToIndex - docsDone);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/*
	 * BlackLab index version history:
	 * 1. Initial version
	 * 2. Sort index added to forward index; multiple forward indexes possible
	 */

	public ContentStore getContentStore(String fieldName) {
		return searcher.getContentStore(fieldName);
	}

	/**
	 * Get our index directory
	 * @return the index directory
	 */
	public File getIndexLocation() {
		return searcher.getIndexDirectory();
	}

	/**
	 * Set parameters we would like to be passed to the DocIndexer class
	 * @param indexerParam the parameters
	 */
	public void setIndexerParam(Map<String, String> indexerParam) {
		this.indexerParam = indexerParam;
	}

	/**
	 * Get the parameters we would like to be passed to the DocIndexer class.
	 *
	 * Used by DocIndexer classes to get their parameters.
	 * @return the parameters
	 */
	public Map<String, String> getIndexerParameters() {
		return indexerParam;
	}

	/**
	 * Get the IndexWriter we're using.
	 *
	 * Useful if e.g. you want to access FSDirectory.
	 *
	 * @return the IndexWriter
	 */
	protected IndexWriter getWriter(){
		return searcher.getWriter();
	}

//	/**
//	 * Set the template for the indexmetadata.json file for a new index.
//	 *
//	 * The template determines whether and how fields are tokenized/analyzed,
//	 * indicates which fields are title/author/date/pid fields, and provides
//	 * extra (optional) information like display names and descriptions.
//	 *
//	 * This method should be called just after creating the new index. It cannot
//	 * be used on existing indices; if you need to change something about your
//	 * index metadata, edit the file directly (but be careful, as it of course
//	 * will not affect already-indexed data).
//	 *
//	 * @param indexTemplateFile the JSON file to use as a template.
//	 */
//	public void setNewIndexMetadataTemplate(File indexTemplateFile) {
//		searcher.getIndexStructure().setNewIndexMetadataTemplate(indexTemplateFile);
//	}

	public Searcher getSearcher() {
		return searcher;
	}

    /**
     * Set the directories to search for linked files.
     *
     * DocIndexerXPath allows us to index a second file into the
     * same Lucene document, which is useful for external metadata, etc.
     * This determines how linked files are located.
     *
     * @param linkedFileDirs directories to search
     */
    public void setLinkedFileDirs(List<File> linkedFileDirs) {
        this.linkedFileDirs.clear();
        this.linkedFileDirs.addAll(linkedFileDirs);
    }

    /**
     * Add a directory to search for linked files.
     *
     * DocIndexerXPath allows us to index a second file into the
     * same Lucene document, which is useful for external metadata, etc.
     * This determines how linked files are located.
     *
     * @param linkedFileDir directory to search
     */
    public void addLinkedFileDir(File linkedFileDir) {
        this.linkedFileDirs.add(linkedFileDir);
    }

    public File getLinkedFile(String inputFile) {
        File f = new File(inputFile);
        if (f.exists())
            return f; // either absolute or relative to current dir
        if (f.isAbsolute())
            return null; // we tried absolute, but didn't find it
        // Look in the configured directories for the relative path
        return FileUtil.findFile(linkedFileDirs, inputFile, null);
    }

}
