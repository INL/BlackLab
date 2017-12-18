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

import java.io.IOException;
import java.io.Reader;

import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.util.CountingReader;

/**
 * Abstract base class for a DocIndexer processing XML files.
 */
public abstract class DocIndexerAbstract extends DocIndexer {
	/**
	 * Write content chunks per 10M (i.e. don't keep all content in memory at all times)
	 */
	private static final long WRITE_CONTENT_CHUNK_SIZE = 10000000;

	protected boolean skippingCurrentDocument = false;

	protected CountingReader reader;

	/** Total words processed by this indexer. Used for reporting progress, do not reset except when finished with file. */
	protected int wordsDone = 0;
	private int wordsDoneAtLastReport = 0;

	//protected ContentStore contentStore;

	private StringBuilder content = new StringBuilder();

	/** Are we capturing the content of the document for indexing? */
	private boolean captureContent = false;

	/** What field we're capturing content for */
	private String captureContentFieldName;

	private int charsContentAlreadyStored = 0;

	protected int nDocumentsSkipped = 0;

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
	@Override
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

    /** NOTE: newer DocIndexers should only have a default constructor, and provide methods to set
     * the Indexer object and the document being indexed (which are called by the Indexer). This
     * allows us more flexibility in how we supply the document to this object (e.g. as a file, a
     * byte array, an inputstream, a reader, ...), which helps if we want to use e.g. VTD-XML and
     * could allow us to re-use DocIndexers in the future.
     */
    public DocIndexerAbstract() {
    }

	public DocIndexerAbstract(Indexer indexer, String fileName, Reader reader) {
		setIndexer(indexer);
		setDocumentName(fileName);
		setDocument(reader);
	}

    /**
     * Set the document to index.
     * @param reader document
     */
    @Override
    public void setDocument(Reader reader) {
        this.reader = new CountingReader(reader);
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }

    @Override
    public final void reportCharsProcessed() {
		long charsProcessed = reader.getCharsReadSinceLastCall();
		indexer.getListener().charsDone(charsProcessed);
	}

    /**
     * Report the change in wordsDone since the last report
     */
    @Override
    public final void reportTokensProcessed() {
    	int wordsDoneSinceLastReport = 0;

    	if (wordsDoneAtLastReport > wordsDone) // reset by child class?
    		wordsDoneSinceLastReport = wordsDone;
    	else
    		wordsDoneSinceLastReport = wordsDone - wordsDoneAtLastReport;

    	indexer.getListener().tokensDone(wordsDoneSinceLastReport);
    	wordsDoneAtLastReport = wordsDone;
    }

}
