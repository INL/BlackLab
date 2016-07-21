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

/**
 * Used to report progress while indexing, so we can give feedback to the user.
 */
public class IndexListener {
	private long indexStartTime;

	public long getIndexStartTime() {
		return indexStartTime;
	}

	private long closeStartTime;

	private long indexTime = 0;

	private long optimizeTime = 0;

	private long closeTime = 0;

	/** How many documents have been processed? */
	private int docsDone = 0;

	/** How many characters have been processed? */
	private long charsProcessed = 0;

	/** How many tokens (words) have been processed? */
	private long tokensProcessed = 0;

	/** How many files have been processed? */
	private long filesProcessed = 0;

	private long createTime;

	private long totalTime;

	private int errors = 0;

	/**
	 * Started processing a file.
	 *
	 * Synchronized to allow parallel indexing.
	 *
	 * @param name
	 *            name of the file
	 */
	public synchronized void fileStarted(String name) {
		//
	}

	public synchronized void fileDone(String name) {
		filesProcessed++;
	}

	/**
	 * Some number of characters has been processed.
	 *
	 * (this should be called regularly by the indexing class, so we can provide accurate progress
	 * reports)
	 *
	 * Synchronized to allow parallel indexing.
	 *
	 * @param charsDone
	 *            the number of characters processed since the last call
	 */
	public synchronized void charsDone(long charsDone) {
		charsProcessed += charsDone;
	}

	/**
	 * We started processing a document
	 *
	 * Synchronized to allow parallel indexing.
	 *
	 * @param name
	 *            name of the document
	 */
	public synchronized void documentStarted(String name) {
		//
	}

	/**
	 * A document was processed.
	 *
	 * Synchronized to allow parallel indexing.
	 *
	 * @param name
	 *            name of the document
	 */
	public synchronized void documentDone(String name) {
		docsDone++;
	}

	/**
	 * Get the number of files processed so far.
	 *
	 * @return the number of files processed so far
	 */
	public synchronized long getFilesProcessed() {
		return filesProcessed;
	}

	/**
	 * Get the number of characters processed so far.
	 *
	 * @return the number of characters processed so far
	 */
	public synchronized long getCharsProcessed() {
		return charsProcessed;
	}

	/**
	 * Get the number of characters processed so far.
	 *
	 * @return the number of characters processed so far
	 */
	public synchronized long getTokensProcessed() {
		return tokensProcessed;
	}

	/**
	 * Get the number of documents processed so far.
	 *
	 * @return the number of documents processed so far
	 */
	public synchronized long getDocsDone() {
		return docsDone;
	}

	/**
	 * The indexing process started
	 */
	public void indexStart() {
		indexStartTime = System.currentTimeMillis();
	}

	/**
	 * The indexing process ended
	 */
	public void indexEnd() {
		indexTime = System.currentTimeMillis() - indexStartTime;
	}

	/**
	 * The close process started
	 */
	public void closeStart() {
		closeStartTime = System.currentTimeMillis();
	}

	/**
	 * The close process ended
	 */
	public void closeEnd() {
		closeTime = System.currentTimeMillis() - closeStartTime;
	}

	public long getIndexTime() {
		return indexTime;
	}

	public long getOptimizeTime() {
		return optimizeTime;
	}

	public long getCloseTime() {
		return closeTime;
	}

	public void indexerCreated(Indexer indexer) {
		createTime = System.currentTimeMillis();
	}

	public void indexerClosed() {
		totalTime = System.currentTimeMillis() - createTime;
	}

	public long getTotalTime() {
		return totalTime;
	}

	public void luceneDocumentAdded() {
		//
	}

	public void tokensDone(int n) {
		tokensProcessed += n;
	}

	/**
	 * An index error occurred.
	 *
	 * Subclasses may override this method to perform any special error-related
	 * action, such as moving the failed file to an error directory, etc.
	 *
	 * @param error type of error, i.e. "not found"
	 * @param unitType type of indexing unit, i.e. "file", "zip", "tgz"
	 * @param unit the indexing unit in which the error occurred
	 * @param subunit optional subunit (i.e. which file inside zip, or null for regular files)
	 * @return true if indexing should continue
	 */
	public boolean errorOccurred(String error, String unitType, File unit, File subunit) {
		errors++;
		return true;
	}

	public int getErrors() {
		return errors;
	}

	/**
	 * Changes will be rolled back (called after an error).
	 */
	public void rollbackStart() {
		// (subclass may override this)
	}

	/**
	 * Changes have been rolled back (called after an error).
	 */
	public void rollbackEnd() {
		// (subclass may override this)
	}

}
