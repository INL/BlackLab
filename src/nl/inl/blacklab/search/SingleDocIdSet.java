/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search;

import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;

/**
 * Used for finding hits in a single document (for highlighting).
 */
class SingleDocIdSet extends DocIdSet {
	final int id;

	public SingleDocIdSet(int id) {
		this.id = id;
	}

	@Override
	public DocIdSetIterator iterator() {
		return new DocIdSetIterator() {
			private boolean nexted = false;

			private boolean done = false;

			@Override
			public int nextDoc() {
				if (done)
					return NO_MORE_DOCS;
				nexted = true;
				return id;
			}

			@Override
			public int docID() {
				if (!nexted || done)
					return NO_MORE_DOCS;
				return id;
			}

			@Override
			public int advance(int target) {
				if (done || target > id)
					return NO_MORE_DOCS;
				return id;
			}
		};
	}
}