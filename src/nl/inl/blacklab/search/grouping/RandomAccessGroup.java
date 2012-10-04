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
package nl.inl.blacklab.search.grouping;

import java.util.Iterator;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Hits.ConcType;
import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.lucene.SpansWithHit;

import org.apache.lucene.search.spans.Spans;

/**
 * A group of results, with its group identity and the results themselves, that you can access
 * randomly (i.e. you can obtain a list of Hit objects)
 */
public class RandomAccessGroup extends Group {
	Hits results;

	// public RandomAccessGroup(Searcher searcher, GroupIdentity groupIdentity)
	// {
	// this(searcher, groupIdentity, null);
	// }

	public RandomAccessGroup(Searcher searcher, String groupIdentity,
			String humanReadableGroupIdentity, String defaultConcField) {
		super(groupIdentity, humanReadableGroupIdentity);
		results = new Hits(searcher, defaultConcField);
	}

	public Hits getHits() {
		return results;
	}

	public int size() {
		return results.size();
	}

	public void add(Hit result) {
		results.add(result);
	}

	@Override
	public String toString() {
		return "GroupOfHits, identity = " + groupIdentity + ", size = " + results.size();
	}

	@Override
	public Spans getSpans() {
		return new SpansWithHit() {
			Iterator<Hit> it = results.iterator();

			Hit currentHit = null;

			@Override
			public int doc() {
				return currentHit.doc;
			}

			@Override
			public int end() {
				return currentHit.end;
			}

			@Override
			public boolean next() {
				if (!it.hasNext())
					return false;
				currentHit = it.next();
				return true;
			}

			@Override
			public int start() {
				return currentHit.start;
			}

			@Override
			public Hit getHit() {
				return currentHit;
			}

		};
	}

	protected void setConcordanceStatus(String concField, ConcType concType) {
		results.setConcordanceStatus(concField, concType);
	}

	// @Override
	// public Iterator<Hit> iterator()
	// {
	// return results.iterator();
	// }

}
