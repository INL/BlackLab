/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.search.spans.Spans;

/**
 * Limits results like a SQL LIMIT clause
 *
 * NOTE: UNLIKE the SQL LIMIT clause, indices are 0-based! So specifying 0 for start and 10 for max
 * would retrieve the first 10 results.
 */
public class SpansLimiter extends SpansAbstract {
	private int start;

	private int max;

	private Spans source;

	int sourceIndex = -1;

	private boolean wouldHaveMore = false;

	public boolean wouldHaveHadMore() {
		return wouldHaveMore;
	}

	/**
	 * Construct ResultsLimiter
	 *
	 * NOTE: indices are 0-based!
	 *
	 * @param source
	 *            the result set to limit
	 * @param start
	 *            the first result (0-based)
	 * @param max
	 *            the maximum number of results
	 */
	public SpansLimiter(Spans source, int start, int max) {
		this.source = source;
		this.start = start;
		this.max = max;
	}

	@Override
	public boolean next() throws IOException {
		if (sourceIndex + 1 >= start + max) {
			wouldHaveMore = source.next();
			return false;
		}
		sourceIndex++;
		boolean more = source.next();
		while (more && sourceIndex < start) {
			sourceIndex++;
			more = source.next();
		}
		return more;
	}

	@Override
	public int doc() {
		return source.doc();
	}

	@Override
	public int end() {
		return source.end();
	}

	@Override
	public int start() {
		return source.start();
	}

}
