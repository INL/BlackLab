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
import nl.inl.blacklab.search.indexstructure.FieldType;
import nl.inl.util.CountingReader;

/**
 * Abstract base class for a DocIndexer processing XML files.
 */
public abstract class DocIndexerAbstract implements DocIndexer {
	/**
	 * Write content chunks per 10M (i.e. don't keep all content in memory at all times)
	 */
	private static final long WRITE_CONTENT_CHUNK_SIZE = 10000000;

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

	/** Do we want to omit norms? (Default: yes) */
	public boolean omitNorms = true;

	/**
	 * Returns our Indexer object
	 * @return the Indexer object
	 */
	public Indexer getIndexer() {
		return indexer;
	}

	/**
	 * Enables or disables norms. Norms are disabled by default.
	 *
	 * The method name was chosen to match Lucene's Field.setOmitNorms().
	 * Norms are only required if you want to use document-length-normalized scoring.
	 *
	 * @param b if true, doesn't store norms; if false, does store norms
	 */
	public void setOmitNorms(boolean b) {
		omitNorms = b;
	}

	boolean continueIndexing() {
		return indexer.continueIndexing();
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

	private void appendContentInternal(String str) {
		content.append(str);
	}

	public void appendContent(String str) {
		appendContentInternal(str);
		if (content.length() >= WRITE_CONTENT_CHUNK_SIZE) {
			storePartCapturedContent();
		}
	}

	public void appendContent(char[] buffer, int start, int length) {
		appendContentInternal(new String(buffer, start, length));
		if (content.length() >= WRITE_CONTENT_CHUNK_SIZE) {
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

	/**
	 * Returns the current position in the original XML content in chars.
	 * @return the current char position
	 */
	public int getCharacterPosition() {
		return charsContentAlreadyStored + content.length();
	}

	/**
	 * Provided for compatibility with Meertens' fork of BlackLab;
	 * will eventually be removed in favor of an "official" way
	 * to index content without storing in a ContentStore.
	 *
	 * Not sure if this will always give the correct position because
	 * the original input is used for highlighting, not the reconstructed
	 * XML in the content variable.
	 *
	 * @return the content length
	 * @deprecated will be handled differently in the future.
	 */
	@Deprecated
	public int getContentPositionNoStore(){
		return content.length();
	}

	public DocIndexerAbstract(Indexer indexer, String fileName, Reader reader) {
		this.indexer = indexer;
		this.fileName = fileName;
		this.reader = new CountingReader(reader);

		// Get our parameters from the indexer
		Map<String, String> indexerParameters = indexer.getIndexerParameters();
		if (indexerParameters != null)
			setParameters(indexerParameters);
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
	protected Map<String, String> parameters = new HashMap<>();

	@Override
	public boolean hasParameter(String name) {
		return parameters.containsKey(name);
	}

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

	protected boolean tokenizeField(String name) {
		String parName = name + "_tokenized";
		if (!hasParameter(name + "_tokenized")) {
			parName = name + "_analyzed"; // Check the old (Lucene 3.x) term, "analyzed"
		}

		return getParameter(parName, true);
	}

	/*
	@Override
	public FieldType getMetadataFieldType(String fieldName) {
		return tokenizeField(fieldName) ? indexer.metadataFieldTypeTokenized: indexer.metadataFieldTypeUntokenized;
	}
	*/

	@Override
	public FieldType getMetadataFieldTypeFromIndexerProperties(String fieldName) {
		if (tokenizeField(fieldName))
			return FieldType.TEXT;
		return FieldType.UNTOKENIZED;
	}

	protected org.apache.lucene.document.FieldType luceneTypeFromIndexStructType(FieldType type) {
		switch (type) {
		case NUMERIC:
			throw new IllegalArgumentException("Numeric types should be indexed using IntField, etc.");
		case TEXT:
			return indexer.metadataFieldTypeTokenized;
		case UNTOKENIZED:
			return indexer.metadataFieldTypeUntokenized;
		default:
			throw new IllegalArgumentException("Unknown field type: " + type);
		}
	}

}
