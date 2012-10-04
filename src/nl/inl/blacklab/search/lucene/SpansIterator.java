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
import java.util.Iterator;

import nl.inl.blacklab.search.Hit;

import org.apache.lucene.search.spans.Spans;

/**
 * Iterate over a Spans object, yielding Hit objects.
 */
class SpansIterator implements Iterator<Hit> {
	Hit lookAhead = null;

	boolean more;

	private Spans spans;

	public SpansIterator(Spans spans) {
		this.spans = spans;
		try {
			more = spans.next();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		if (more)
			lookAhead = Hit.getHit(spans);
	}

	@Override
	public boolean hasNext() {
		return more;
	}

	@Override
	public Hit next() {
		Hit rv = lookAhead;
		try {
			more = spans.next();
			if (more)
				lookAhead = Hit.getHit(spans);
			else
				lookAhead = null;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		return rv;
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

}