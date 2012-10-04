/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.sequences;

import java.io.IOException;
import java.util.Collection;

import org.apache.lucene.search.spans.Spans;

/**
 * Expands the source spans to the left and right by the given ranges.
 *
 * This is used to support sequences including subsequences of completely unknown tokens (like
 * "apple /2..4 pear" to find apple and pear with 2 to 4 tokens in between).
 *
 * Note that this class will generate all possible expansions, so if you call it with left-expansion
 * of between 2 to 4 tokens, it will generate 3 new hits for every hit from the source spans: one
 * hit with 2 more tokens to the left, one hit with 3 more tokens to the left, and one hit with 4
 * more tokens to the left.
 *
 * Note that the hits coming out of this class may contain duplicates and will not always be
 * properly sorted. Both are undesirable; the user doesn't want to see duplicates, and Lucene
 * expects Spans to always be sorted by start point (and possible by end point after that, although
 * that is not certain; should be checked).
 *
 * Therefore, objects of this class should be wrapped in a class that sort the matches per document
 * and eliminates duplicates.
 *
 * NOTE: because we don't know the field length at this time, expansion to the right may actually
 * yield hits that are beyond the end of the field content. This is not a problem when wild card
 * tokens occur in the middle of a pattern, but when the wild card tokens can occur at the end of
 * the pattern, you should detect and remove the invalid hits later.
 */
class SpansExpansionRaw extends Spans {
	private Spans clause;

	private boolean more = true;

	private boolean expandToLeft;

	private int min;

	private int max;

	private int start;

	private int end;

	private int expandStepsLeft = 0;

	public SpansExpansionRaw(Spans clause, boolean expandToLeft, int min, int max) {
		this.clause = clause;
		this.expandToLeft = expandToLeft;
		this.min = min;
		this.max = max;
		if (min > max)
			throw new RuntimeException("min > max");
		if (min < 0 || max < 0)
			throw new RuntimeException("negative expansions not supported");
	}

	/**
	 * @return the Lucene document id of the current hit
	 */
	@Override
	public int doc() {
		return clause.doc();
	}

	/**
	 * @return end position of current hit
	 */
	@Override
	public int end() {
		return end;
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

		if (expandStepsLeft > 0) {
			expandStepsLeft--;
			if (expandToLeft) {
				start--;

				// Valid expansion?
				if (start >= 0)
					return true;

				// Can't finish the expansion because we're at the start
				// of the document; go to next hit.
			} else {
				// We can't validate the end expansion because we don't know the length of the
				// content of this field. So expansion to the right always succeeds, and may
				// yield invalid hits. If a query ends with wildcard tokens, you should be aware of
				// this and take steps to eliminate these invalid hits.
				end++;
				return true;
			}
		}

		more = clause.next();
		if (more)
			return resetExpand();
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
		if (clause.doc() < doc) {
			more = clause.skipTo(doc);
			if (more)
				return resetExpand();
			return false;
		}
		return next(); // per Lucene's specification, always at least go to the next hit
	}

	/**
	 * The source clause is at a new hit. Reset the expansion process.
	 *
	 * Note that we may discover we can't do the minimum expansion (because the hit is at the start
	 * of the document, for example), so we may have to advance the clause again, and may actually
	 * run out of hits while doing so.
	 *
	 * @return true if we're at a valid hit and have reset the expansion, false if we're done
	 * @throws IOException
	 */
	private boolean resetExpand() throws IOException {
		while (true) {
			// Attempt to do the initial expansion and reset the counter
			start = clause.start();
			end = clause.end();
			if (expandToLeft)
				start -= min;
			else
				end += min;
			expandStepsLeft = max - min;

			// Valid expansion?
			if (start >= 0)
				return true; // Yes, return

			// No, try the next hit, if there one
			if (!next())
				return false; // No hits left, we're done
		}
	}

	/**
	 * @return start of the current hit
	 */
	@Override
	public int start() {
		return start;
	}

	@Override
	public String toString() {
		return "SpansExpansion(" + clause + ", " + expandToLeft + ", " + min + ", " + max + ")";
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
