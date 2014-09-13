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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import nl.inl.blacklab.search.Hit;

import org.apache.lucene.search.spans.Spans;

public class HelpersForTests {
	static class ListResults extends BLSpans {
		private Iterator<Hit> iterator;

		Hit current = null;

		public ListResults(List<Hit> results) {
			iterator = results.iterator();
		}

		@Override
		public boolean next() {
			if (iterator.hasNext()) {
				current = iterator.next();
				return true;
			}
			return false;
		}

		@Override
		public int doc() {
			return current.doc;
		}

		@Override
		public int end() {
			return current.end;
		}

		@Override
		public int start() {
			return current.start;
		}

		@Override
		public Hit getHit() {
			return current;
		}

		@Override
		public void setHitQueryContext(HitQueryContext context) {
			// just ignore this here
		}
	}

	public static Spans getSimpleResults(int n) {
		List<Hit> list = new ArrayList<Hit>();
		for (int i = 0; i < n; i++) {
			int base = 3 * i;
			list.add(new Hit(base + 1, base + 2, base + 3));
		}
		return new ListResults(list);
	}

}
