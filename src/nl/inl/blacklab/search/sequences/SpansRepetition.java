/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.sequences;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import nl.inl.blacklab.search.Hit;

import org.apache.lucene.search.spans.Spans;

/**
 * Finds all sequences of consecutive hits from the source spans of the specified min and max
 * lengths. Used to implement repetition operators.
 */
class SpansRepetition extends Spans {
	private SpansInBuckets source;

	List<Hit> hitsInBucket = null;

	Iterator<Hit> bucketIterator;

	boolean more = true;

	private int min;

	private int max;

	private int firstToken;

	private int tokenLength;

	public SpansRepetition(Spans source, int min, int max) {
		// Find all consecutive matches in this Spans
		this.source = new SpansInBucketsConsecutive(source);
		this.min = min;
		this.max = max;
		if (max != -1 && min > max)
			throw new RuntimeException("min > max");
		if (min < 1)
			throw new RuntimeException("min < 1");
	}

	/**
	 * @return the Lucene document id of the current hit
	 */
	@Override
	public int doc() {
		return source.doc();
	}

	/**
	 * @return end position of current hit
	 */
	@Override
	public int end() {
		return hitsInBucket.get(firstToken + tokenLength - 1).end;
	}

	/**
	 * Go to the next match.
	 *
	 * @return true if we're on a valid match, false if we're done.
	 * @throws IOException
	 */
	@Override
	public boolean next() throws IOException {
		if (!more)
			return false;

		if (hitsInBucket != null) {
			// We have a bucket.

			// Go to the next hit length for this start point.
			tokenLength++;

			// Find the first valid hit in the bucket
			if ((max != -1 && tokenLength > max) || firstToken + tokenLength > hitsInBucket.size()) {
				// On to the next start point.
				firstToken++;
				tokenLength = min;
			}

			if (firstToken + tokenLength <= hitsInBucket.size()) {
				// Still a valid rep. hit.
				return true;
			}

			// No valid hits left; on to the next bucket
		}

		// Next bucket
		more = source.next();
		if (more)
			return resetRepeat();
		return false;
	}

	/**
	 * Go to the specified document, if it has hits. If not, go to the next document containing
	 * hits.
	 *
	 * @param doc
	 *            the document number to skip to / over
	 * @return true if we're at a valid hit, false if not
	 * @throws IOException
	 */
	@Override
	public boolean skipTo(int doc) throws IOException {
		if (!more)
			return false;
		more = source.skipTo(doc);
		if (more)
			return resetRepeat();
		return false;
	}

	private boolean resetRepeat() throws IOException {
		while (true) {
			hitsInBucket = source.getHits();
			if (hitsInBucket.size() >= min) {
				// This stretch is large enough to get a repetition hit!
				firstToken = 0;
				tokenLength = min;
				return true;
			}

			// Not large enough; next bucket
			more = source.next();
			if (!more)
				return false;
		}
	}

	/**
	 * @return start of the current hit
	 */
	@Override
	public int start() {
		return hitsInBucket.get(firstToken).start;
	}

	@Override
	public String toString() {
		return "SpansRepetition(" + source + ", " + min + ", " + max + ")";
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
