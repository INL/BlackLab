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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Constructor;
import java.text.Collator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import nl.inl.blacklab.exceptions.DocumentFormatException;
import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.index.complex.ComplexFieldProperty;
import nl.inl.blacklab.search.Searcher;
import nl.inl.util.FileUtil;
import nl.inl.util.TarGzipReader;
import nl.inl.util.TarGzipReader.FileHandler;
import nl.inl.util.UnicodeReader;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;

/**
 * Tool for indexing. Reports its progress to an IndexListener.
 */
public class Indexer {

	static final Logger logger = Logger.getLogger(Searcher.class);

	/** Our index */
	Searcher searcher;

	/** Stop after indexing this number of docs. -1 if we shouldn't stop. */
	int maxNumberOfDocsToIndex = -1;

	/** Should we terminate indexing? (e.g. because of an error) */
	boolean terminateIndexing = false;

	/**
	 * Where to report indexing progress.
	 */
	IndexListener listener = null;

	/**
	 * Have we reported our creation and the start of indexing to the listener yet?
	 */
	boolean createAndIndexStartReported = false;

	/**
	 * When we encounter a zip or tgz file, do we descend into it like it was a directory?
	 */
	boolean processArchivesAsDirectories = true;

	/**
	 * Recursively index files inside a directory? (or archive file, if processArchivesAsDirectories == true)
	 */
	boolean recurseSubdirs = true;

	/**
	 * The class to instantiate for indexing documents. This class must be able to
	 * deal with the file format of the input files.
	 */
	Class<? extends DocIndexer> docIndexerClass;

	/** If an error occurs (e.g. an XML parse error), should we
	 *  try to continue indexing, or abort? */
	boolean continueAfterInputError = true;

	/** If an error occurs (e.g. an XML parse error), and we don't
	 * continue indexing, should we re-throw it, or assume the client
	 * picked it up in the listener and return normally? */
	boolean rethrowInputError = true;

	/**
	 * Parameters we should pass to our DocIndexers upon instantiation.
	 */
	Map<String, String> indexerParam;

	/** How to index metadata fields (tokenized) */
	FieldType metadataFieldTypeTokenized;

	/** How to index metadata fields (untokenized) */
	FieldType metadataFieldTypeUntokenized;

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
	 * Set the collator to use for sorting (passed to ForwardIndex to keep a sorted list of terms).
	 * Defaults to English collator.
	 * @param collator the collator
	 * @deprecated use Searcher.setDefaultCollator()
	 */
	@Deprecated
	static public void setCollator(Collator collator) {
		Searcher.setDefaultCollator(collator);
	}

	/**
	 * When we encounter a zipfile, do we descend into it like it was a directory?
	 *
	 * @param b
	 *            if true, treats zipfiles like a directory and processes all the files inside
	 * @deprecated renamed to setProcessArchivesAsDirectories()
	 */
	@Deprecated
	public void setProcessZipFilesAsDirectories(boolean b) {
		setProcessArchivesAsDirectories(b);
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
		this.recurseSubdirs = recurseSubdirs;
	}

	/**
	 * Construct Indexer
	 *
	 * @param directory
	 *            the main BlackLab index directory
	 * @param create
	 *            if true, creates a new index; otherwise, appends to existing index
	 * @param docIndexerClass how to index the files
	 * @param contentsFieldName name of the main contents field
	 * @throws IOException
	 * @throws DocumentFormatException if no DocIndexer was specified and autodetection failed
	 * @deprecated use version without contents field name
	 */
	@Deprecated
	public Indexer(File directory, boolean create, Class<? extends DocIndexer> docIndexerClass,
			String contentsFieldName) throws IOException, DocumentFormatException {
		this(directory, create, docIndexerClass);
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
	 */
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
	 */
	public Indexer(File directory, boolean create)
			throws IOException, DocumentFormatException {
		this(directory, create, null, (File)null);
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
	 */
	public Indexer(File directory, boolean create,
			Class<? extends DocIndexer> docIndexerClass, File indexTemplateFile) throws DocumentFormatException, IOException {
		this.docIndexerClass = docIndexerClass;

		searcher = Searcher.openForWriting(directory, create, indexTemplateFile);
		if (!create)
			searcher.getIndexStructure().setModified();

		if (this.docIndexerClass == null) {
			// No DocIndexer supplied; try to detect it from the index
			// metadata.
			String formatId = searcher.getIndexStructure().getDocumentFormat();
			if (formatId != null && formatId.length() > 0)
				setDocIndexer(DocumentFormats.getIndexerClass(formatId));
			else {
				throw new DocumentFormatException("Cannot detect document format for index!");
			}
		}

		metadataFieldTypeTokenized = new FieldType();
		metadataFieldTypeTokenized.setStored(true);
		metadataFieldTypeTokenized.setIndexed(true);
		metadataFieldTypeTokenized.setTokenized(true);
		metadataFieldTypeTokenized.setOmitNorms(true); // @@@ <-- depending on setting?
		metadataFieldTypeTokenized.setStoreTermVectors(true);
		metadataFieldTypeTokenized.setStoreTermVectorPositions(true);
		metadataFieldTypeTokenized.setStoreTermVectorOffsets(true);
		metadataFieldTypeTokenized.freeze();

		metadataFieldTypeUntokenized = new FieldType(metadataFieldTypeTokenized);
		metadataFieldTypeUntokenized.setTokenized(false);
		metadataFieldTypeUntokenized.freeze();
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
	 * Set number of documents after which we should stop.
	 * Useful when testing.
	 * @param n number of documents after which to stop
	 * @deprecated renamed to setMaxNumberOfDocsToIndex
	 */
	@Deprecated
	public void setMaxDocs(int n) {
		setMaxNumberOfDocsToIndex(n);
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
	 */
	public void setDocIndexer(Class<? extends DocIndexer> docIndexerClass) {
		this.docIndexerClass = docIndexerClass;
	}

	/**
	 * Called to create a new instance of DocIndexer.
	 *
	 * @param documentName the name of the data to index
	 * @param reader what to index
	 * @return the DocIndexer
	 * @throws Exception if the DocIndexer could not be instantiated for some reason
	 */
	protected DocIndexer createDocIndexer(String documentName, Reader reader) throws Exception {
		// Instantiate our DocIndexer class
		Constructor<? extends DocIndexer> constructor = docIndexerClass.getConstructor(
				Indexer.class, String.class, Reader.class);
		DocIndexer docIndexer = constructor.newInstance(this, documentName, reader);
		return docIndexer;
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
		searcher.getWriter().addDocument(document, searcher.getAnalyzer());
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
	 * Add a list of tokens to a forward index.
	 *
	 * @param fieldName what forward index to add this to
	 * @param tokens the tokens to add
	 * @return the id assigned to the content
	 */
	public int addToForwardIndex(String fieldName, List<String> tokens) {
		ForwardIndex forwardIndex = searcher.getForwardIndex(fieldName);
		if (forwardIndex == null)
			throw new RuntimeException("No forward index for field " + fieldName);

		return forwardIndex.addDocument(tokens);
	}

	/**
	 * Add a list of tokens to a forward index
	 *
	 * @param fieldName what forward index to add this to
	 * @param prop the property to get values and position increments from
	 * @return the id assigned to the content
	 */
	public int addToForwardIndex(String fieldName, ComplexFieldProperty prop) {
		return addToForwardIndex(fieldName, prop.getValues(), prop.getPositionIncrements());
	}

	/**
	 * Add a list of tokens to a forward index
	 *
	 * @param fieldName what forward index to add this to
	 * @param tokens the tokens to add
	 * @param posIncr position increment associated with each token
	 * @return the id assigned to the content
	 */
	public int addToForwardIndex(String fieldName, List<String> tokens, List<Integer> posIncr) {
		ForwardIndex forwardIndex = searcher.getForwardIndex(fieldName);
		if (forwardIndex == null)
			throw new RuntimeException("No forward index for field " + fieldName);

		return forwardIndex.addDocument(tokens, posIncr);
	}

	/**
	 * Index a document from a Reader, using the specified type of DocIndexer
	 *
	 * @param documentName
	 *            some (preferably unique) name for this document (for example, the file
	 *            name or path)
	 * @param reader
	 *            where to index from
	 * @throws Exception
	 */
	private void indexReader(String documentName, Reader reader) throws Exception {
		getListener().fileStarted(documentName);
		int docsDoneBefore = searcher.getWriter().numDocs();
		long tokensDoneBefore = getListener().getTokensProcessed();

		DocIndexer docIndexer = createDocIndexer(documentName, reader);

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
	 * Index a document from a Reader, using the specified type of DocIndexer
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
			indexReader(documentName, reader);
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

	 * By default, indexes only .xml files in a directory.
	 *
	 * Recurses into subdirs only if that setting is enabled.
	 *
	 * @param file
	 *            the input file or directory
	 * @throws Exception
	 */
	public void index(File file) throws Exception {
		indexInternal(file, "*.xml", recurseSubdirs);
	}

	/**
	 * Index the file or directory specified.
	 *
	 * By default, indexes only .xml files in a directory.
	 *
	 * @param fileToIndex
	 *            the input file or directory
	 * @param recurseSubdirs
	 *            recursively index subdirectories? (overrides the setting)
	 * @throws UnsupportedEncodingException
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws Exception
	 * @deprecated recurseSubdirs is now a setting instead of a parameter (defaults to true)
	 */
	@Deprecated
	public void index(File fileToIndex, boolean recurseSubdirs)
			throws UnsupportedEncodingException, FileNotFoundException, IOException, Exception {
		 // default glob
		index(fileToIndex, "*.xml", recurseSubdirs);
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
		indexInternal(fileToIndex, glob, recurseSubdirs);
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
	 * @deprecated recurseSubdirs is now a setting instead of a parameter (defaults to true)
	 */
	@Deprecated
	public void index(File fileToIndex, String glob, boolean recurseSubdirs)
			throws UnsupportedEncodingException, FileNotFoundException, IOException, Exception {
		indexInternal(fileToIndex, glob, recurseSubdirs);
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
	private void indexInternal(File fileToIndex, String glob, boolean recurseSubdirs)
			throws UnsupportedEncodingException, FileNotFoundException, IOException, Exception {
		String fn = fileToIndex.getCanonicalPath(); //Name();
		if (fileToIndex.isDirectory()) {
			indexDir(fileToIndex, glob, recurseSubdirs);
		} else {
			if (fn.endsWith(".zip")) {
				indexZip(fileToIndex, glob, recurseSubdirs);
			} else {
				if (!isSpecialOperatingSystemFile(fileToIndex)) { // skip special OS files
					try {
						FileInputStream is = new FileInputStream(fileToIndex);
						try {
							indexInputStream(fn, is, glob, recurseSubdirs);
						} finally {
							is.close();
						}
					} catch (IOException e) {
						log("*** Error indexing " + fileToIndex, e);
						terminateIndexing = !getListener().errorOccurred(e.getMessage(), "file", fileToIndex, null);
					}
				}
			}
		}
	}

	/**
	 * Index from an InputStream
	 *
	 * @param name
	 *            name for the InputStream (e.g. name of the file)
	 * @param is
	 *            the stream
	 * @param glob what files to index inside an archive
	 * @param recurseArchives whether or not to index archives inside archives
	 * @throws IOException
	 * @throws Exception
	 */
	void indexInputStream(String name, InputStream is, String glob, boolean recurseArchives) {
		try {
			if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
				indexTarGzip(name, is, glob, recurseArchives);
			} else if (name.endsWith(".gz")) {
				indexGzip(name, is);
			} else if (name.endsWith(".zip")) {
				// TODO InputStream version of zip, for zips inside another archive
				logger.warn("Skipped " + name + ", ZIPs inside archives not yet supported");
			} else {
				Reader reader = new BufferedReader(new UnicodeReader(is, "utf-8"));
				try {
					indexReader(name, reader);
				} finally {
					// NOTE: don't close the reader as the caller will close the stream when
					// appropriate! When processing archive files, the stream may need to remain
					// open for the next entry.
				}
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Index a directory
	 *
	 * @param dir
	 *            directory to index
	 * @param glob what files in the dir to index
	 * @param recurseSubdirs
	 *            recursively process subdirectories?
	 * @throws UnsupportedEncodingException
	 * @throws FileNotFoundException
	 * @throws Exception
	 * @throws IOException
	 */
	private void indexDir(File dir, String glob, boolean recurseSubdirs) throws UnsupportedEncodingException,
			FileNotFoundException, IOException, Exception {
		if (!dir.exists())
			throw new FileNotFoundException("Input dir not found: " + dir);
		if (!dir.isDirectory())
			throw new IOException("Specified input dir is not a directory: " + dir);
		for (File fileToIndex : FileUtil.listFilesSorted(dir)) {
			indexInternal(fileToIndex, glob, recurseSubdirs);
			if (!continueIndexing())
				break;
		}
	}

	/**
	 * Index files inside a zip file.
	 *
	 * Note that directory structure inside the zip file is ignored; files are indexed as if they
	 * are one large directory.
	 *
	 * Also note that for accessing large ZIP files, you need Java 7 which supports the
	 * ZIP64 format, otherwise you'll get the "invalid CEN header (bad signature)" error.
	 *
	 * @param zipFile
	 *            the zip file
	 * @param glob
	 *            what files in the zip to process
	 * @param recurseArchives whether to process archives inside archives
	 * @throws Exception
	 */
	private void indexZip(File zipFile, String glob, boolean recurseArchives) throws Exception {
		if (!zipFile.exists())
			throw new FileNotFoundException("ZIP file not found: " + zipFile);
		Pattern pattGlob = Pattern.compile(FileUtil.globToRegex(glob));
		try {
			ZipFile z = new ZipFile(zipFile);
			try {
				Enumeration<? extends ZipEntry> es = z.entries();
				while (es.hasMoreElements()) {
					ZipEntry e = es.nextElement();
					if (e.isDirectory())
						continue;
					String fileName = e.getName();
					Matcher m = pattGlob.matcher(fileName);
					boolean isArchive = fileName.endsWith(".zip") || fileName.endsWith(".gz") || fileName.endsWith(".tgz");
					if (m.matches() || isArchive) {
						try {
							InputStream is = z.getInputStream(e);
							try {
								if (isArchive) {
									if (recurseArchives && processArchivesAsDirectories)
										indexInputStream(fileName, is, glob, recurseArchives);
								} else {
									indexInputStream(fileName, is, glob, recurseArchives);
								}
							} finally {
								is.close();
							}
						} catch (ZipException ex) {
							log("*** Error indexing " + fileName + " from " + zipFile, ex);
							terminateIndexing = !getListener().errorOccurred(ex.getMessage(), "zip", zipFile, new File(fileName));
						}
					}
					if (!continueIndexing())
						break;
				}
			} finally {
				z.close();
			}
		} catch (ZipException e) {
			log("*** Error opening zip file: " + zipFile, e);
			// continue trying other files!
		}
	}

	public void indexGzip(final String gzFileName, InputStream gzipStream) {
		if (!TarGzipReader.canProcessTarGzip()) {
			// Apache commons-compress not found, skip file
			terminateIndexing = !getListener().errorOccurred("Cannot index .gz, Apache common-compress not on classpath", "tgz", new File(gzFileName), null);
			//logger.warn("Skipping " + tgzFileName + ", Apache common-compress not found on classpath!");
			return;
		}
		TarGzipReader.processGzip(gzFileName, gzipStream, new FileHandler() {
			@Override
			public boolean handle(String filePath, InputStream contents) {
				try {
					indexInputStream(filePath, contents, "*", false);
				} catch (Exception e) {
					log("*** Error indexing .gz file: " + filePath, e);
					terminateIndexing = !getListener().errorOccurred(e.getMessage(), "gz", new File(filePath), new File(filePath));
				}
				return continueIndexing();
			}
		});
	}

	public void indexTarGzip(final String tgzFileName, InputStream tarGzipStream, final String glob, final boolean recurseArchives) {
		if (!TarGzipReader.canProcessTarGzip()) {
			// Apache commons-compress not found, skip file
			terminateIndexing = !getListener().errorOccurred("Cannot index .tar.gz, Apache common-compress not on classpath", "tgz", new File(tgzFileName), null);
			//logger.warn("Skipping " + tgzFileName + ", Apache common-compress not found on classpath!");
			return;
		}
		final Pattern pattGlob = Pattern.compile(FileUtil.globToRegex(glob));
		TarGzipReader.processTarGzip(tarGzipStream, new FileHandler() {
			@Override
			public boolean handle(String filePath, InputStream contents) {
				try {
					File f = new File(filePath);
					String fn = f.getName();
					Matcher m = pattGlob.matcher(fn);
					if (m.matches()) {
						String entryName = tgzFileName + File.separator + filePath;
						indexInputStream(entryName, contents, glob, recurseArchives);
					} else {
						boolean isArchive = fn.endsWith(".zip") || fn.endsWith(".gz") || fn.endsWith(".tgz");
						if (isArchive) {
							if (recurseArchives && processArchivesAsDirectories) {
								indexInputStream(tgzFileName + File.pathSeparator + filePath, contents, glob, recurseArchives);
							}
						}
					}
				} catch (Exception e) {
					log("*** Error indexing tgz file: " + tgzFileName, e);
					terminateIndexing = !getListener().errorOccurred(e.getMessage(), "tgz", new File(tgzFileName), new File(filePath));
				}
				return continueIndexing();
			}
		});
	}

	/**
	 * Index a list of files.
	 *
	 * If the list contains a directory, the whole directory is indexed, including subdirs.
	 *
	 * @param listFile
	 *            list of files to index (assumed to reside in or under basedir)
	 * @throws Exception
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws UnsupportedEncodingException
	 * @deprecated not really used, implement your own if required
	 */
	@Deprecated
	public void indexFileList(File listFile) throws UnsupportedEncodingException,
			FileNotFoundException, IOException, Exception {
		indexFileList(listFile, null);
	}

	/**
	 * Index a list of files.
	 *
	 * If the list contains a directory, the whole directory is indexed, including subdirs.
	 *
	 * @param listFile
	 *            list of files to index (assumed to reside in or under basedir)
	 * @param inputDir
	 *            basedir for the files to index, or null if the list file contains absolute paths
	 * @throws Exception
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws UnsupportedEncodingException
	 * @deprecated not really used, implement your own if required
	 */
	@Deprecated
	public void indexFileList(File listFile, File inputDir) throws UnsupportedEncodingException,
			FileNotFoundException, IOException, Exception {
		if (!listFile.exists())
			throw new FileNotFoundException("List file not found: " + listFile);
		if (inputDir != null && !inputDir.isDirectory())
			throw new FileNotFoundException("Dir not found: " + inputDir);
		List<String> filesToRead = FileUtil.readLines(listFile);
		for (String filePath: filesToRead) {
			File fileToIndex;
			if (inputDir == null)
				fileToIndex = new File(filePath);
			else
				fileToIndex = new File(inputDir, filePath);
			index(fileToIndex);
			if (!continueIndexing())
				break;
		}
	}

	/**
	 * Index a list of files.
	 *
	 * If the list contains a directory, the whole directory is indexed, including subdirs.
	 *
	 * @param list
	 *            the list of files
	 * @throws Exception
	 * @throws IOException
	 * @throws FileNotFoundException
	 * @throws UnsupportedEncodingException
	 * @deprecated not really used, implement your own if required
	 */
	@Deprecated
	public void indexFileList(List<File> list) throws UnsupportedEncodingException,
			FileNotFoundException, IOException, Exception {
		for (File fileToIndex: list) {
			index(fileToIndex);
			if (!continueIndexing())
				break;
		}
	}

	/**
	 * Should we skip the specified file because it is a special OS file?
	 *
	 * @param file
	 *            the file
	 * @return true if we should skip it, false otherwise
	 */
	protected boolean isSpecialOperatingSystemFile(File file) {
		return file.getName().equals("Thumbs.db");
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
}
