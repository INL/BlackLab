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
import java.util.Collection;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.search.spans.Spans;

/**
 * Returns all tokens that do not occur in the matches
 * of the specified query.
 *
 * Each token is returned as a single hit.
 */
class SpansNot extends Spans {
	/** The spans to invert */
	private Spans clause;

	/** Are there more hits in our spans? */
	private boolean moreHitsInClause;

	/** Are we completely done? */
	private boolean done;

	/** Current document */
	private int currentDoc;

	/** Current document length */
	private int currentDocLength;

	/** Current document length */
	private int currentToken;

	/** Name of the field we're searching */
	private String fieldName;

	/** The Lucene index reader, for querying field length */
	private IndexReader reader;

	/** For testing, we don't have an IndexReader available, so we use test values */
	private boolean useTestValues = false;

	/** Did we check if the field length is stored separately in the index? */
	private boolean lookedForLengthField = false;

	/** Is the field length stored separately in the index? */
	private boolean lengthFieldIsStored = false;

	/** Field name to check for the length of the field in tokens */
	private String lengthTokensFieldName;

	/** For testing, we don't have an IndexReader available, so we use test values.
	 *
	 *  The test values are: there are 3 documents (0, 1 and 2) and each is 5 tokens long.
	 *
	 *  @param test whether or not we want to use test values
	 */
	void setTest(boolean test) {
		this.useTestValues = test;
	}

	public SpansNot(IndexReader reader, String fieldName, Spans clause) {
		this.reader = reader;
		this.fieldName = fieldName;
		this.clause = clause;
		lengthTokensFieldName = ComplexFieldUtil.fieldName(fieldName, "length_tokens");

		done = false;
		moreHitsInClause = true;
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
				moreHitsInClause = clause.next();

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
	 *
	 * @throws IOException
	 */
	private boolean nextDoc() {
		int maxDoc = useTestValues ? 3 : reader.maxDoc();
		do {
			currentDoc++;
		} while (currentDoc < maxDoc && (useTestValues ? false : reader.isDeleted(currentDoc)) );

		if (currentDoc == maxDoc) {
			done = true;
			return false; // no more docs; we're done
		}
		currentDocLength = getCurrentDocFieldLength();
		currentToken = 0;
		return true;
	}

	/**
	 * Get the number of indexed tokens for our field in the current document.
	 *
	 * Used to produce all tokens that aren't hits in our clause.
	 *
	 * @return the number of tokens
	 */
	private int getCurrentDocFieldLength() {

		if (useTestValues)
			return 5; // while testing, all documents are 10 tokens long

		if (!lookedForLengthField || lengthFieldIsStored)  {
			// We either know the field length is stored in the index,
			// or we haven't checked yet and should do so now.
			try {
				Document document = reader.document(currentDoc);
				String strLength = document.get(lengthTokensFieldName);
				lookedForLengthField = true;
				if (strLength != null) {
					// Yes, found the field length stored in the index.
					// Parse and return it.
					lengthFieldIsStored = true;
					return Integer.parseInt(strLength);
				}
				// No, length field is not stored in the index. Always use term vector from now on.
				lengthFieldIsStored = false;
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}

		// Calculate the total field length by adding all the term frequencies.
		// (much slower)
		try {
			TermFreqVector tfv = reader.getTermFreqVector(currentDoc, fieldName);
			if (tfv == null) {

				// No term frequency vector. We have to assume this is because no tokens were
				// stored for this document (document is empty)
				return 0;

				/*
				Document d = reader.document(currentDoc);
				for (Fieldable f: d.getFields()) {
					System.err.println(f.name() + ": " + f.stringValue());
				}

				throw new RuntimeException("No term frequency vector found for field " + fieldName + " (doc " + currentDoc + ")");
				*/

			}
			int [] tfs = tfv.getTermFrequencies();
			if (tfs == null)
				throw new RuntimeException("No term frequencies found for field " + fieldName + " (doc " + currentDoc + ")");
			int n = 0;
			for (int tf: tfs) {
				n += tf;
			}
			return n;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
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
		// Skip clause to doc (or beyond if there's no hits in doc)
		moreHitsInClause = clause.skipTo(doc);

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
		return "NotSpans(" + clause + ")";
	}

	@Override
	public Collection<byte[]> getPayload() {
		return null;
	}

	@Override
	public boolean isPayloadAvailable() {
		return false;
	}

}
