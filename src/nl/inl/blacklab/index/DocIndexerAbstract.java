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

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.search.Searcher;
import nl.inl.util.CountingReader;

import org.apache.lucene.document.Field;

/**
 * Abstract base class for a DocIndexer processing XML files.
 */
public abstract class DocIndexerAbstract implements DocIndexer {
	/**
	 * Write content chunks per 10M (i.e. don't keep all content in memory at all times)
	 */
	private static final long WRITE_CONTENT_CHUNK_SIZE = 10000000;

	/**
	 * [DEBUG] Skip documents that are WRITE_CONTENT_CHUNK_SIZE chars or longer?
	 */
	protected static boolean SKIP_LARGE_DOCUMENTS = false;

	protected boolean skippingCurrentDocument = false;

	public Indexer indexer;

	/**
	 * File we're currently parsing. This can be useful for storing the original filename in the
	 * index.
	 */
	public String fileName;

	protected CountingReader reader;

	//protected ContentStore contentStore;

	private StringBuilder content = new StringBuilder();

	/** Are we capturing the content of the document for indexing? */
	private boolean captureContent = false;

	/** What field we're capturing content for */
	private String captureContentFieldName;

	private int charsContentAlreadyStored = 0;

	protected int nDocumentsSkipped = 0;

	/**
	 * The setting to use when creating Field objects that we want to be analyzed
	 * (i.e., with or without norms; default is without)
	 */
	public Field.Index indexAnalyzed = Field.Index.ANALYZED_NO_NORMS;

	/**
	 * The setting to use when creating Field objects that we don't want to be analyzed
	 * (i.e., with or without norms; default is without)
	 */
	public Field.Index indexNotAnalyzed = Field.Index.NOT_ANALYZED_NO_NORMS;

	/**
	 * Enables or disables norms. Norms are disabled by default.
	 *
	 * The method name was chosen to match Lucene's Field.setOmitNorms().
	 * Norms are only required if you want to use document-length-normalized scoring.
	 *
	 * @param b if true, doesn't store norms; if false, does store norms
	 */
	public void setOmitNorms(boolean b) {
		indexAnalyzed = b ? Field.Index.ANALYZED_NO_NORMS : Field.Index.ANALYZED;
		indexNotAnalyzed = b ? Field.Index.NOT_ANALYZED_NO_NORMS : Field.Index.NOT_ANALYZED;
	}

	boolean continueIndexing() {
		return indexer.continueIndexing();
	}

	/**
	 * Starts capturing of content.
	 * @deprecated pass the field name you're capturing content for
	 */
	@Deprecated
	public void startCaptureContent() {
		startCaptureContent(Searcher.DEFAULT_CONTENTS_FIELD_NAME);
	}

	public void startCaptureContent(String fieldName) {
		captureContent = true;
		captureContentFieldName = fieldName;

		// Empty the StringBuilder object
		content.setLength(0);
	}

	public int storeCapturedContent() {
		captureContent = false;
		int id = -1;
		if (!skippingCurrentDocument) {
			ContentStore contentStore = indexer.getContentStore(captureContentFieldName);
			id = contentStore.store(content.toString());
		}
		content.setLength(0);
		charsContentAlreadyStored = 0;
		return id;
	}

	public void storePartCapturedContent() {
		charsContentAlreadyStored += content.length();
		if (!skippingCurrentDocument) {
			ContentStore contentStore = indexer.getContentStore(captureContentFieldName);
			contentStore.storePart(content.toString());
		}
		content.setLength(0);
	}

	protected void appendContentInternal(String str) {
		content.append(str);
	}

	public void appendContent(String str) {
		appendContentInternal(str);
		if (content.length() >= WRITE_CONTENT_CHUNK_SIZE) {
			if (SKIP_LARGE_DOCUMENTS && !skippingCurrentDocument) {
				nDocumentsSkipped++;
				System.err.println("Skipping large document!");
				skippingCurrentDocument = true; // too large
			}

			storePartCapturedContent();
		}
	}

	public void appendContent(char[] buffer, int start, int length) {
		appendContentInternal(new String(buffer, start, length));
		if (content.length() >= WRITE_CONTENT_CHUNK_SIZE) {
			if (SKIP_LARGE_DOCUMENTS && !skippingCurrentDocument) {
				nDocumentsSkipped++;
				System.err.println("Skipping large document!");
				skippingCurrentDocument = true; // too large
			}

			storePartCapturedContent();
		}
	}

	public void processContent(char[] buffer, int start, int length) {
		if (captureContent)
			appendContent(buffer, start, length);
	}

	public void processContent(String contentToProcess) {
		if (captureContent)
			appendContent(contentToProcess);
	}

	public int getContentPosition() {
		return charsContentAlreadyStored + content.length();
	}

	public DocIndexerAbstract(Indexer indexer, String fileName, Reader reader) {
		this.indexer = indexer;
		this.fileName = fileName;
		this.reader = new CountingReader(reader);

		// Get our parameters from the indexer
		setParameters(indexer.getIndexerParameters());
	}

	public void reportCharsProcessed() {
		long charsProcessed = reader.getCharsReadSinceLastCall();
		indexer.getListener().charsDone(charsProcessed);
	}

	public void reportTokensProcessed(int n) {
		indexer.getListener().tokensDone(n);
	}

	/**
	 * Parameters passed to this indexer
	 */
	private Map<String, String> parameters = new HashMap<String, String>();

	@Override
	public void setParameter(String name, String value) {
		parameters.put(name, value);
	}

	@Override
	public void setParameters(Map<String, String> param) {
		for (Map.Entry<String, String> e: param.entrySet()) {
			parameters.put(e.getKey(), e.getValue());
		}
	}

	@Override
	public String getParameter(String name, String defaultValue) {
		String value = parameters.get(name);
		if (value == null)
			return defaultValue;
		return value;
	}

	@Override
	public String getParameter(String name) {
		return getParameter(name, null);
	}

	public boolean getParameter(String name, boolean defaultValue) {
		String value = parameters.get(name);
		if (value == null)
			return defaultValue;
		value = value.trim().toLowerCase();
		return value.equals("true") || value.equals("1") || value.equals("yes");
	}

	public int getParameter(String name, int defaultValue) {
		String value = parameters.get(name);
		if (value == null)
			return defaultValue;
		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

}
