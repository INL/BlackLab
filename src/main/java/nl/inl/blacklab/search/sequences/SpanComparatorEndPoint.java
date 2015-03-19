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
package nl.inl.blacklab.search.sequences;

import java.util.Comparator;

import nl.inl.blacklab.search.Hit;


/**
 * Compare two hits (assumed to be in the same document) by end point, then by start point.
 *
 * So the following hits:
 *
 * <pre>
 * (1, 2)
 * (1, 3)
 * (2, 2)
 * (2, 4)
 * (3, 3)
 * </pre>
 *
 * Would be sorted as follows:
 *
 * <pre>
 * (1, 2)
 * (2, 2)
 * (1, 3)
 * (3, 3)
 * (2, 4)
 * </pre>
 */
public class SpanComparatorEndPoint implements Comparator<Hit> {
	@Override
	public int compare(Hit o1, Hit o2) {
		if (o2.end != o1.end)
			return o1.end - o2.end;

		return o1.start - o2.start;
	}
}
