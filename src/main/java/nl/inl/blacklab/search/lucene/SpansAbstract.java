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

import org.apache.lucene.search.spans.Spans;

/**
 * SpansAbstract is our abstract base class for implementing our own Spans
 * classes. It implements a naive default implementation for skipTo. Derived
 * classes may choose to provide their own, more efficient version.
 *
 * It also provides default implementations for getPayload() and
 * isPayloadAvailable().
 */
public abstract class SpansAbstract extends Spans {

	@Override
	public abstract int nextDoc() throws IOException;

	@Override
	public int advance(int target) throws IOException {
		// Naive implementation:
		int doc;
		while ((doc = nextDoc()) < target) {
			// do nothing
		}
		return doc;
	}

	@Override
	public abstract int docID();

	@Override
	public abstract int nextStartPosition() throws IOException;

	@Override
	public abstract int startPosition();

	@Override
	public abstract int endPosition();

	/**
	 * @throws IOException
	 *             on IO error
	 */
	@Override
	public Collection<byte[]> getPayload() throws IOException {
		return null;
	}

	@Override
	public long cost() {
		// returns a completely arbitrary constant value, but it's for
		// optimizing scoring and we don't generally use that
		return 100;
	}

	/**
	 * @throws IOException
	 *             on IO error
	 */
	@Override
	public boolean isPayloadAvailable() throws IOException {
		return false;
	}

}
