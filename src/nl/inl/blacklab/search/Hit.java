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
package nl.inl.blacklab.search;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.search.lucene.SpansWithHit;

import org.apache.lucene.search.spans.Spans;

/**
 * Class for a hit. Normally, hits are iterated over in a Lucene Spans object, but in some places,
 * it makes sense to place hits in separate objects: when caching or sorting hits, or just for
 * convenience in client code.
 *
 * This class has public members for the sake of efficiency; this makes a non-trivial difference
 * when iterating over hundreds of thousands of hits.
 */
public class Hit implements Comparable<Hit> {
	/**
	 * Get the hit object from a Spans object.
	 *
	 * This method makes sure Hit objects aren't reinstantiated unnecessarily, as well as making
	 * sure Hit subclass objects aren't squashed back into regular Hit objects.
	 *
	 * Subclasses of Hit should implement their own version of this function to make sure they
	 * return the proper type.
	 *
	 * @param spans
	 *            the Spans to get the Hit from
	 * @return the Hit [subclass] object
	 */
	public static Hit getHit(Spans spans) {
		if (spans instanceof SpansWithHit) {
			// There's already a Hit [subclass] object available; return that
			return ((SpansWithHit) spans).getHit();
		}

		// Nothing available yet; just instantiate a new basic hit object
		return new Hit(spans.doc(), spans.start(), spans.end());
	}

	/**
	 * Retrieve a list of Hit objects from a Spans.
	 *
	 * @param spans
	 *            where to retrieve the hits
	 * @return the list of hits
	 */
	public static List<Hit> hitList(Spans spans) {
		List<Hit> result = new ArrayList<Hit>();
		try {
			while (spans.next()) {
				result.add(getHit(spans));
			}
			return result;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public boolean equals(Object with) {
		if (this == with)
			return true;
		if (with instanceof Hit) {
			Hit o = (Hit) with;
			return doc == o.doc && start == o.start && end == o.end;
		}
		return false;
	}

	@Override
	public int compareTo(Hit o) {
		if (this == o)
			return 0;
		if (doc == o.doc) {
			if (start == o.start) {
				return end - o.end;
			}
			return start - o.start;
		}
		return doc - o.doc;
	}

	/** The Lucene doc this hits occurs in */
	public int doc;

	/** End of this hit's span (in word positions).
	 *
	 *  Note that this actually points to the first word not in the hit (just like Spans).
	 */
	public int end;

	/** Start of this hit's span (in word positions) */
	public int start;

	/** Context information */
	public int[] context;

	/** Where in the context array the hit text starts */
	public int contextHitStart;

	/** Where in the context array the right context starts */
	public int contextRightStart;

	/** How many words one context takes up (context[] may contain multiple contexts) */
	public int contextLength;

	/**
	 * Construct a hit object
	 *
	 * @param doc
	 *            the document
	 * @param start
	 *            start of the hit (word positions)
	 * @param end
	 *            end of the hit (word positions)
	 */
	public Hit(int doc, int start, int end) {
		this.doc = doc;
		this.start = start;
		this.end = end;
	}

	@Override
	public String toString() {
		return String.format("doc %d, words %d-%d", doc, start, end);
	}

	@Override
	public int hashCode() {
		return (doc * 17 + start) * 31 + end;
	}

}
