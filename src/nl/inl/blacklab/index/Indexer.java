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
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.externalstorage.ContentStoreDirZip;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.util.FileUtil;
import nl.inl.util.UnicodeReader;
import nl.inl.util.Utilities;
import nl.inl.util.VersionFile;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;

/**
 * Tool for indexing. Reports its progress to an IndexListener.
 */
public class Indexer {
	/** Our index */
	private IndexWriter writer;

	/** Our forward index */
	private ForwardIndex forwardIndex;

	/** Our content store (where we store the full XML of (part of) the document) */
	private ContentStore contentStore;

	/** Stop after indexing this number of docs. -1 if we shouldn't stop. */
	private int maxDocs = -1;

	/** The location of the index */
	private File indexLocation;

	/**
	 * Where to report indexing progress. Default: dummy listener.
	 */
	private IndexListener listener = new IndexListener();

	/**
	 * For using different analyzers per field
	 *
	 * @deprecated Changed in Lucene and not used in BlackLab anyway. We may re-implement this in
	 *             the future, but differently.
	 */
	@Deprecated
	private PerFieldAnalyzerWrapper analyzer;

	/**
	 * When we encounter a zipfile, do we descend into it like it was a directory?
	 */
	private boolean processZipFilesAsDirectories = true;

	/**
	 * Should we close the Lucene index, forward index and contentstore in close()? (Yes if we
	 * opened them; no if they were passed to us)
	 */
	private boolean closeIndexes = false;

	private Class<? extends DocIndexer> docIndexerClass;

	/**
	 * The collator to use for sorting (passed to ForwardIndex to keep a sorted list of terms).
	 * Defaults to English collator.
	 */
	Collator collator = Collator.getInstance(new Locale("en", "GB"));


	/** If an error, like a parse error, should we
	 *  try to continue indexing, or abort? */
	private boolean continueAfterInputError = false;

	/** If an error, like a parse error, should we
	 *  try to continue indexing, or abort?
	 *  @param b if true, continue; if false, abort
	 */
	public void setContinueAfterInputError(boolean b) {
		continueAfterInputError = b;
	}

	/**
	 * When we encounter a zipfile, do we descend into it like it was a directory?
	 *
	 * @param b
	 *            if true, treats zipfiles like a directory and processes all the files inside
	 */
	public void setProcessZipFilesAsDirectories(boolean b) {
		processZipFilesAsDirectories = b;
	}

	public IndexListener getListener() {
		return listener;
	}

	/**
	 * Construct Indexer that reports progress to stdout.
	 *
	 * @param directory
	 *            the main BlackLab index directory
	 * @param create
	 *            if true, creates a new index; otherwise, appends to existing index
	 * @throws IOException
	 */
	public Indexer(File directory, boolean create, Class<? extends DocIndexer> docIndexerClass)
			throws IOException {
		this(directory, create, docIndexerClass, new IndexListenerReportConsole());
	}

	/**
	 * Construct Indexer
	 *
	 * @param directory
	 *            the main BlackLab index directory
	 * @param create
	 *            if true, creates a new index; otherwise, appends to existing index
	 * @param listener
	 *            where to report our progress
	 * @throws IOException
	 */
	public Indexer(File directory, boolean create, Class<? extends DocIndexer> docIndexerClass,
			IndexListener listener) throws IOException {
		this.docIndexerClass = docIndexerClass;

		writer = openIndexWriter(directory, create);
		indexLocation = directory;
		forwardIndex = new ForwardIndex(new File(directory, "forward"), true, collator, create);
		contentStore = new ContentStoreDirZip(new File(directory, "xml"), create);
		closeIndexes = true; // we opened them, so we should close them again

		this.listener = listener;
		listener.indexerCreated(this);
		listener.indexStart();
	}

	/**
	 * Construct Indexer
	 *
	 * @param writer
	 *            the index to write to
	 * @param listener
	 *            where to report our progress
	 * @throws IOException
	 */
	@Deprecated
	public Indexer(IndexWriter writer, IndexListener listener) throws IOException {
		this(writer, null, null, listener);
	}

	/**
	 * Construct Indexer that reports progress to stdout.
	 *
	 * @param writer
	 *            the index to write to
	 * @param forwardIndex
	 *            the forward index (or null if none)
	 * @param contentStore
	 *            the content store
	 * @throws IOException
	 */
	@Deprecated
	public Indexer(IndexWriter writer, ForwardIndex forwardIndex, ContentStore contentStore)
			throws IOException {
		this(writer, forwardIndex, contentStore, new IndexListenerReportConsole());
	}

	/**
	 * Construct Indexer.
	 *
	 * @param writer
	 *            the index to write to
	 * @param forwardIndex
	 *            the forward index (or null if none)
	 * @param contentStore
	 *            the content store
	 * @param listener
	 *            where to report our progress (if null, doesn't report anything)
	 * @throws IOException
	 */
	@Deprecated
	public Indexer(IndexWriter writer, ForwardIndex forwardIndex, ContentStore contentStore,
			IndexListener listener) throws IOException {
		this.writer = writer;
		this.forwardIndex = forwardIndex;
		this.contentStore = contentStore;
		clearFieldAnalyzers();
		this.listener = listener == null ? new IndexListenerDevNull() : listener;
		listener.indexerCreated(this);
		listener.indexStart();
	}

	/**
	 * Construct Indexer
	 *
	 * @param writer
	 *            the index to write to
	 * @param forwardIndex
	 *            the forward index (or null if none)
	 * @param listener
	 *            where to report our progress
	 * @throws IOException
	 */
	@Deprecated
	public Indexer(IndexWriter writer, ForwardIndex forwardIndex, IndexListener listener)
			throws IOException {
		this(writer, forwardIndex, null, listener);
	}

	private void log(String msg, IOException e) {
		// @@@ TODO write to file. log4j?
		e.printStackTrace();
		System.err.println(msg);
	}

	/**
	 * Add specific analyzer for a field
	 *
	 * @deprecated Changed in Lucene and not used in BlackLab anyway. We may re-implement this in
	 *             the future, but differently.
	 * @param fieldName
	 *            field name
	 * @param analyzer
	 *            analyzer
	 */
	@Deprecated
	public void setFieldAnalyzer(String fieldName, Analyzer analyzer) {
		this.analyzer.addAnalyzer(fieldName, analyzer);
	}

	/**
	 * @deprecated Changed in Lucene and not used in BlackLab anyway. We may re-implement this in
	 *             the future, but differently.
	 */
	@Deprecated
	public void clearFieldAnalyzers() {
		analyzer = new PerFieldAnalyzerWrapper(writer.getAnalyzer());
	}

	public void setMaxDocs(int maxDocs) {
		this.maxDocs = maxDocs;
	}

	/**
	 * Optimize index
	 *
	 * @deprecated Because Lucene's optimize is (no longer necessary)
	 *
	 * @throws CorruptIndexException
	 * @throws IOException
	 */
	@Deprecated
	public void optimize() throws CorruptIndexException, IOException {
		listener.optimizeStart();
		writer.optimize();
		listener.optimizeEnd();
	}

	/**
	 * Close the index
	 *
	 * @throws IOException
	 * @throws CorruptIndexException
	 */
	public void close() throws CorruptIndexException, IOException {
		listener.indexEnd();

		// optimize();
		listener.closeStart();

		if (closeIndexes) {
			// NOTE: we should close the IndexWriter here too, but that could
			// break legacy applications (we used to always close the IndexWriter even
			// though it was passed to us by the client). We should eventually change this.
			if (forwardIndex != null)
				forwardIndex.close();
			if (contentStore != null)
				contentStore.close();
		}

		writer.close();

		listener.closeEnd();
		listener.indexerClosed();
	}

	public void setDocIndexer(Class<? extends DocIndexer> docIndexerClass) {
		this.docIndexerClass = docIndexerClass;
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
	public void index(String documentName, Reader reader) throws Exception {
		try {
			listener.fileStarted(documentName);

			DocIndexer docIndexer = createDocIndexer(documentName, reader);

			docIndexer.index();
			listener.fileDone(documentName);
		} catch (InputFormatException e) {
			if (continueAfterInputError) {
				System.err.println("Parsing " + documentName + " failed:");
				e.printStackTrace();
				System.err.println("(continuing indexing)");
			} else {
				// Don't continue; re-throw the exception so we eventually abort
				System.err.println("Input error while processing " + documentName);
				throw e;
			}
		} catch (Exception e) {
			if (continueAfterInputError) {
				System.err.println("Parsing " + documentName + " failed:");
				e.printStackTrace();
				System.err.println("(continuing indexing)");
			} else {
				System.err.println("Exception while processing " + documentName);
				throw e;
			}
		}
	}

	/**
	 * Called to create a new instance of DocIndexer.
	 *
	 * @param documentName the name of the data to index
	 * @param reader what to index
	 * @return the DocIndexer
	 * @throws Exception if the DocIndexer could not be instantiated for some reason
	 */
	protected DocIndexer createDocIndexer(String documentName, Reader reader)
			throws Exception {
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
		writer.addDocument(document /* , analyzer */);
		listener.luceneDocumentAdded();
	}

	public int addToForwardIndex(List<String> tokens) {
		if (forwardIndex != null)
			return forwardIndex.addDocument(tokens);
		return -1;
	}

	/**
	 * Index the file or directory specified.
	 *
	 * @param file
	 *            the input file or directory
	 * @throws Exception
	 */
	public void index(File file) throws Exception {
		index(file, true);
	}

	/**
	 * Index the file or directory specified.
	 *
	 * @param fileToIndex
	 *            the input file or directory
	 * @param recurseSubdirs
	 *            recursively index subdirectories?
	 * @throws Exception
	 */
	public void index(File fileToIndex, boolean recurseSubdirs)
			throws UnsupportedEncodingException, FileNotFoundException, IOException, Exception {
		if (fileToIndex.isDirectory()) {
			if (recurseSubdirs)
				indexDir(fileToIndex, recurseSubdirs);
		} else if (fileToIndex.getName().endsWith(".zip")) {
			if (recurseSubdirs && processZipFilesAsDirectories) {
				indexZip(fileToIndex);
			}
		} else {
			if (!skipFile(fileToIndex)) { // skip special thumbnails file
				try {
					indexFile(fileToIndex);
				} catch (IOException e) {
					log("*** Error indexing " + fileToIndex, e);
					// continue trying other files!
				}
			}
		}
	}

	/**
	 * Index a specific file using the specified type of DocIndexer
	 *
	 * @param file
	 *            file to index
	 * @throws UnsupportedEncodingException
	 * @throws FileNotFoundException
	 * @throws Exception
	 * @throws IOException
	 */
	public void indexFile(File file) throws UnsupportedEncodingException, FileNotFoundException,
			Exception, IOException {
		FileInputStream is = new FileInputStream(file);
		try {
			indexInputStream(file.getName(), is);
		} finally {
			is.close();
		}
	}

	/**
	 * Index from an InputStream
	 *
	 * @param name
	 *            name for the InputStream (e.g. name of the file)
	 * @param is
	 *            the stream
	 * @throws IOException
	 * @throws Exception
	 */
	private void indexInputStream(String name, InputStream is) throws IOException, Exception {
		Reader reader = new BufferedReader(new UnicodeReader(is, "utf-8"));
		try {
			index(name, reader);
		} finally {
			reader.close();
		}
	}

	/**
	 * Index an entire directory using the specified type of DocIndexer.
	 *
	 * DOES recurse into subdirectories.
	 *
	 * @param dir
	 *            directory to index
	 * @throws UnsupportedEncodingException
	 * @throws FileNotFoundException
	 * @throws Exception
	 * @throws IOException
	 */
	public void index(File dir, String glob, boolean recurseSubdirs)
			throws UnsupportedEncodingException, FileNotFoundException, IOException, Exception {
		Pattern pattGlob = Pattern.compile(FileUtil.globToRegex(glob));
		for (File fileToIndex : dir.listFiles()) {
			boolean indexThis = fileToIndex.isDirectory();
			if (fileToIndex.isFile()) {
				// Regular file; does it match our glob expression?
				Matcher m = pattGlob.matcher(fileToIndex.getName());
				if (m.matches())
					indexThis = true; // yes
			}
			if (indexThis)
				index(fileToIndex, recurseSubdirs);

			if (!continueIndexing())
				break;
		}
	}

	/**
	 * Index an entire directory using the specified type of DocIndexer.
	 *
	 * DOES recurse into subdirectories.
	 *
	 * @param dir
	 *            directory to index
	 * @throws UnsupportedEncodingException
	 * @throws FileNotFoundException
	 * @throws Exception
	 * @throws IOException
	 */
	public void indexDir(File dir) throws UnsupportedEncodingException, FileNotFoundException,
			IOException, Exception {
		indexDir(dir, true);
	}

	/**
	 * Index a specific file using the specified type of DocIndexer
	 *
	 * @param dir
	 *            directory to index
	 * @param recurseSubdirs
	 *            recursively process subdirectories?
	 * @throws UnsupportedEncodingException
	 * @throws FileNotFoundException
	 * @throws Exception
	 * @throws IOException
	 */
	public void indexDir(File dir, boolean recurseSubdirs) throws UnsupportedEncodingException,
			FileNotFoundException, IOException, Exception {
		for (File fileToIndex : dir.listFiles()) {
			index(fileToIndex, recurseSubdirs);
			if (!continueIndexing())
				break;
		}
	}

	/**
	 * Should we skip the specified file?
	 *
	 * @param file
	 *            the file
	 * @return true if we should skip it, false otherwise
	 */
	private boolean skipFile(File file) {
		return file.getName().equals("Thumbs.db");
	}

	/**
	 * Index every file inside a zip file.
	 *
	 * Note that directory structure inside the zip file is ignored; files are indexed as if they
	 * are one large directory.
	 *
	 * @param zipFile
	 *            the zip file
	 * @throws Exception
	 */
	private void indexZip(File zipFile) throws Exception {
		try {
			ZipFile z = new ZipFile(zipFile);
			try {
				Enumeration<? extends ZipEntry> es = z.entries();
				while (es.hasMoreElements()) {
					ZipEntry e = es.nextElement();
					String xmlFileName = e.getName();
					if (xmlFileName.endsWith(".xml")) {
						try {
							InputStream is = z.getInputStream(e);
							try {
								indexInputStream(xmlFileName, is);
							} finally {
								is.close();
							}
						} catch (ZipException ex) {
							log("*** Error indexing " + xmlFileName + " from " + zipFile, ex);
							// continue trying other files!
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
	 */
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
	 */
	public void indexFileList(File listFile, File inputDir) throws UnsupportedEncodingException,
			FileNotFoundException, IOException, Exception {
		List<String> filesToRead = FileUtil.readLines(listFile);
		for (String filePath : filesToRead) {
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
	 */
	public void indexFileList(List<File> list) throws UnsupportedEncodingException,
			FileNotFoundException, IOException, Exception {
		for (File fileToIndex : list) {
			index(fileToIndex);
			if (!continueIndexing())
				break;
		}
	}

	/**
	 * Should we continue indexing or stop (because maxDocs has been reached)?
	 *
	 * @return true if we should continue, false if not
	 */
	public synchronized boolean continueIndexing() {
		if (maxDocs >= 0) {
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
			if (maxDocs < 0)
				return maxDocs;
			return Math.max(0, maxDocs - writer.numDocs());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
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
			VersionFile.write(indexDir, "blacklab", "1");
		else {
			if (!VersionFile.isTypeVersion(indexDir, "blacklab", "1")) {
				throw new RuntimeException("BlackLab index has wrong type or version! "
						+ VersionFile.report(indexDir));
			}
		}

		return writer;
	}

	public ContentStore getContentStore() {
		return contentStore;
	}

	public File getIndexLocation() {
		 return indexLocation;
	}

	public void getDocIndexer() {
		// TODO Auto-generated method stub

	}

}
