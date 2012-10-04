/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collection;

import org.apache.lucene.search.spans.Spans;

/**
 * SpansAbstract is our abstract base class for implementing our own Spans classes. It implements a
 * naive default implementation for skipTo. Derived classes may choose to provide their own, more
 * efficient version.
 *
 * It also provides default implementations for getPayload() and isPayloadAvailable().
 */
public abstract class SpansAbstract extends Spans {
	@Override
	public abstract boolean next() throws IOException;

	@Override
	public boolean skipTo(int target) throws IOException {
		do {
			if (!next())
				return false;
		} while (target > doc());
		return true;
	}

	@Override
	public abstract int doc();

	@Override
	public abstract int start();

	@Override
	public abstract int end();

	/**
	 * @throws IOException
	 *             on IO error
	 */
	@Override
	public Collection<byte[]> getPayload() throws IOException {
		return null;
	}

	@Override
	public boolean isPayloadAvailable() {
		return false;
	}

}
