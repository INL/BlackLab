/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import nl.inl.blacklab.search.Hit;

import org.apache.lucene.search.spans.Spans;

public class HelpersForTests {
	static class ListResults extends SpansWithHit {
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
