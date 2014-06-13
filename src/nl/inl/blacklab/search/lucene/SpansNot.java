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
package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.index.AtomicReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.search.spans.Spans;
import org.apache.lucene.util.Bits;

/**
 * Returns all tokens that do not occur in the matches
 * of the specified query.
 *
 * Each token is returned as a single hit.
 */
class SpansNot extends BLSpans {
	/** The spans to invert */
	private BLSpans clause;

	/** Have we called next() or skipTo on the clause yet? */
	private boolean clauseIterationStarted;

	/** Are there more hits in our spans? */
	private boolean moreHitsInClause;

	/** Are we completely done? */
	private boolean done;

	/** Current document */
	private int currentDoc;

	/** Current document length */
	private long currentDocLength;

	/** Current token position */
	private int currentToken;

	/** The Lucene index reader, for querying field length */
	private AtomicReader reader;

	/** For testing, we don't have an IndexReader available, so we use test values */
	private boolean useTestValues = false;

	/** Used to get the field length in tokens for a document */
	DocFieldLengthGetter lengthGetter;

	/** For testing, we don't have an IndexReader available, so we use test values.
	 *
	 *  The test values are: there are 3 documents (0, 1 and 2) and each is 5 tokens long.
	 *
	 *  @param test whether or not we want to use test values
	 */
	void setTest(boolean test) {
		this.useTestValues = test;
		lengthGetter.setTest(test);
	}

	/**
	 * Constructs a SpansNot.
	 * @param reader the index reader, for getting field lengths
	 * @param fieldName the field name, for getting field lengths
	 * @param clause the clause to invert, or null if we want all tokens
	 */
	public SpansNot(AtomicReader reader, String fieldName, Spans clause) {
		this.reader = reader;
		this.lengthGetter = new DocFieldLengthGetter(reader, fieldName);
		this.clause = clause == null ? null : BLSpansWrapper.optWrap(clause);

		done = false;
		moreHitsInClause = true;
		clauseIterationStarted = false;
		currentDoc = -1;
		currentDocLength = -1;
		currentToken = -1;
	}

	/**
	 * @return the Lucene document id of the current hit
	 */
	@Override
	public int doc() {
		return currentDoc;
	}

	/**
	 * @return end position of current hit
	 */
	@Override
	public int end() {
		return currentToken + 1;
	}

	/**
	 * Go to next span.
	 *
	 * @return true if we're at the next span, false if we're done
	 * @throws IOException
	 */
	@Override
	public boolean next() throws IOException {
		if (done)
			return false;

		// Advance token
		currentToken++;

		boolean foundValidToken = false;
		while (!foundValidToken) {

			// Which of 3 situations are we in?
			if (currentDoc < 0) {
				// A - We haven't started yet.
				//     Go to first document and first hit in clause.
				if (!nextDoc()) {
					return false;
				}
				moreHitsInClause = clause == null ? false : clause.next();
				clauseIterationStarted = true;

				// Loop again to determine if we are at a valid token or not.

			} else if (moreHitsInClause && clause.doc() == currentDoc)  {

				// B - Spans is at currentDoc.
				//     Look at hit, adjust currentToken

				// Current hit beyond currentToken?
				if (clause.start() > currentToken) {

					// Yes. currentToken is fine to produce.
					foundValidToken = true;

				} else {
					// No; advance currentToken past this hit if necessary
					if (clause.end() > currentToken) {
						// (note that end is the first word not in the hit)
						currentToken = clause.end();
					}

					// Now go to next hit and loop again, until we hit the
					// then-part above.
					moreHitsInClause = clause.next();
					clauseIterationStarted = true;
				}

			} else {

				// C - Spans is depleted or is pointing beyond current doc.
				//     Either produce next token (because it's obviously not in
				//     the spans matches), or move to next doc if we're done with this doc.
				if (currentToken < currentDocLength) {

					// Token is fine to produce.
					foundValidToken = true;

				} else {
					// We're done in this document, on to the next
					if (!nextDoc()) {
						// Done with all documents.
						return false;
					}
				}
			}
		}
		return true;
	}

	/**
	 * Go to the next document in the index.
	 *
	 * Advances currentDoc until it finds a non-deleted document,
	 * then determines length and sets currentToken to 0.
	 *
	 * @return true if next document was found, false if there are no more documents.
	 */
	private boolean nextDoc() {
		int maxDoc = useTestValues ? 3 : reader.maxDoc();
		Bits liveDocs = useTestValues ? null : MultiFields.getLiveDocs(reader);
		boolean currentDocIsDeletedDoc;
		do {
			currentDoc++;
			currentDocIsDeletedDoc = liveDocs != null && !liveDocs.get(currentDoc);
 		} while (currentDoc < maxDoc && currentDocIsDeletedDoc);

		if (currentDoc == maxDoc) {
			done = true;
			return false; // no more docs; we're done
		}
		currentDocLength = lengthGetter.getFieldLength(currentDoc);
		currentToken = 0;
		return true;
	}

	/**
	 * Skip to the specified document (or the first document after it containing hits).
	 *
	 * @param doc
	 *            the doc number to skip to (or past)
	 * @return true if we're still pointing to a valid hit, false if we're done
	 * @throws IOException
	 */
	@Override
	public boolean skipTo(int doc) throws IOException {
		// If it's not already (past) there, skip clause
		// to doc (or beyond if there's no hits in doc)
		if (moreHitsInClause && (!clauseIterationStarted || clause.doc() < doc)) {
			moreHitsInClause = clause == null ? false : clause.skipTo(doc);
			clauseIterationStarted = true;
		}

		if (currentDoc >= doc) {
			// We can't skip to it because we're already there or beyond.
			// But, as per spec, skipTo always at least advances to the next match.
			return next();
		}

		// Position currentDoc at doc or first valid doc after
		currentDoc = doc - 1;
		if (!nextDoc())
			return false;

		// Put us at first valid hit
		currentToken = -1;
		return next();
	}

	/**
	 * @return start of current span
	 */
	@Override
	public int start() {
		return currentToken;
	}

	@Override
	public String toString() {
		return clause == null ? "AnyToken()" : "NotSpans(" + clause + ")";
	}

	@Override
	public boolean hitsEndPointSorted() {
		return true;
	}

	@Override
	public boolean hitsStartPointSorted() {
		return true;
	}

	@Override
	public boolean hitsAllSameLength() {
		return true;
	}

	@Override
	public int hitsLength() {
		return 1;
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return true;
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return true;
	}

	@Override
	public boolean hitsAreUnique() {
		return true;
	}

}
