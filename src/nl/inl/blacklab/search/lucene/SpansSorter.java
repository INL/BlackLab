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

import nl.inl.blacklab.search.Searcher;
import nl.inl.blacklab.search.grouping.HitProperty;

import org.apache.lucene.search.spans.Spans;

/**
 * Sort results using some comparator. Subclasses SpansCacher to retrieve all results, then sorts
 * the resulting list.
 */
public class SpansSorter extends SpansCacher {

	public SpansSorter(Searcher searcher, Spans source, HitProperty sortProp,
			String defaultConcField) {
		this(searcher, source, sortProp, false, defaultConcField);
	}

	public SpansSorter(Searcher searcher, Spans source, HitProperty sortProp,
			final boolean reverseSort, String defaultConcField) {
		super(searcher, source, defaultConcField);

		hits.sort(sortProp, reverseSort);

		reset();
	}
}
