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

import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.index.complex.ComplexFieldProperty;
import nl.inl.blacklab.search.Searcher;
import nl.inl.util.FileUtil;
import nl.inl.util.UnicodeReader;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;

/**
 * Tool for indexing. Reports its progress to an IndexListener.
 */
public class Indexer {
	/** Our index */
	protected Searcher searcher;

	/** Stop after indexing this number of docs. -1 if we shouldn't stop. */
	protected int maxDocs = -1;

	/**
	 * Where to report indexing progress.
	 */
	protected IndexListener listener = null;

	/**
	 * Have we reported our creation and the start of indexing to the listener yet?
	 */
	protected boolean createAndIndexStartReported = false;

	/**
	 * When we encounter a zipfile, do we descend into it like it was a directory?
	 */
	protected boolean processZipFilesAsDirectories = true;

	/**
	 * The class to instantiate for indexing documents. This class must be able to
	 * deal with the file format of the input files.
	 */
	protected Class<? extends DocIndexer> docIndexerClass;

	/** If an error occurs (e.g. an XML parse error), should we
	 *  try to continue indexing, or abort? */
	protected boolean continueAfterInputError = true;

	/**
	 * Parameters we should pass to our DocIndexers upon instantiation.
	 */
	protected Map<String, String> indexerParam;

	/** If an error occurs (e.g. an XML parse error), should we
	 *  try to continue indexing, or abort?
	 *  @param b if true, continue; if false, abort
	 */
	public void setContinueAfterInputError(boolean b) {
		continueAfterInputError = b;
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
	 */
	public void setProcessZipFilesAsDirectories(boolean b) {
		processZipFilesAsDirectories = b;
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
	 * @deprecated use version without contents field name
	 */
	@Deprecated
	public Indexer(File directory, boolean create, Class<? extends DocIndexer> docIndexerClass,
			String contentsFieldName) throws IOException {
		this(directory, create, docIndexerClass);
	}

	/**
	 * Construct Indexer
	 *
	 * @param directory
	 *            the main BlackLab index directory
	 * @param create
	 *            if true, creates a new index; otherwise, appends to existing index
	 * @param docIndexerClass how to index the files
	 * @throws IOException
	 */
	public Indexer(File directory, boolean create, Class<? extends DocIndexer> docIndexerClass)
			throws IOException {
		this.docIndexerClass = docIndexerClass;

		searcher = Searcher.openForWriting(directory, create);
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
		// @@@ TODO write to file. log4j?
		e.printStackTrace();
		System.err.println(msg);
	}

	/**
	 * Set number of documents after which we should stop.
	 * Useful when testing.
	 * @param maxDocs number of documents after which to stop
	 */
	public void setMaxDocs(int maxDocs) {
		this.maxDocs = maxDocs;
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

		// Set any parameters for this doc indexer
		for (Map.Entry<String, String> e: indexerParam.entrySet()) {
			docIndexer.setParameter(e.getKey(), e.getValue());
		}

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
		searcher.getWriter().addDocument(document);
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
	public void index(String documentName, Reader reader) throws Exception {
		try {
			getListener().fileStarted(documentName);

			DocIndexer docIndexer = createDocIndexer(documentName, reader);

			docIndexer.index();
			getListener().fileDone(documentName);
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
	 * Index the file or directory specified.
	 *
	 * By default, recurses into subdirectories.
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
	 * @throws UnsupportedEncodingException
	 * @throws FileNotFoundException
	 * @throws IOException
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
			if (!isSpecialOperatingSystemFile(fileToIndex)) { // skip special OS files
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
	 * Index a group of files in a directory.
	 *
	 * @param dir
	 *            directory to index
	 * @param glob what files to index
	 * @param recurseSubdirs whether or not to index subdirectories
	 * @throws UnsupportedEncodingException
	 * @throws FileNotFoundException
	 * @throws Exception
	 * @throws IOException
	 */
	public void index(File dir, String glob, boolean recurseSubdirs)
			throws UnsupportedEncodingException, FileNotFoundException, IOException, Exception {
		if (!dir.exists())
			throw new FileNotFoundException("Input dir not found: " + dir);
		if (!dir.isDirectory())
			throw new IOException("Specified input dir is not a directory: " + dir);
		Pattern pattGlob = Pattern.compile(FileUtil.globToRegex(glob));
		for (File fileToIndex: dir.listFiles()) {
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
	 * Index a specific file using the specified type of DocIndexer
	 *
	 * @param file
	 *            file to index
	 * @throws UnsupportedEncodingException
	 * @throws FileNotFoundException
	 * @throws Exception
	 * @throws IOException
	 */
	private void indexFile(File file) throws UnsupportedEncodingException, FileNotFoundException,
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
	private void indexDir(File dir, boolean recurseSubdirs) throws UnsupportedEncodingException,
			FileNotFoundException, IOException, Exception {
		index(dir, "*", recurseSubdirs);
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
		if (!zipFile.exists())
			throw new FileNotFoundException("ZIP file not found: " + zipFile);
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
			return Math.max(0, maxDocs - searcher.getWriter().numDocs());
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

}
