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

import org.apache.lucene.search.DocIdSet;
import org.apache.lucene.search.DocIdSetIterator;

/**
 * A DocIdSet with a single Lucene document id in it.
 *
 * Used by SingleDocIdFilter (or directly by BlackLabIndex.createSpanQuery) for
 * finding hits in a single document (for highlighting).
 */
public class SingleDocIdSet extends DocIdSet {
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

            @Override
            public long cost() {
                return 100; // (we don't use this)
            }
        };
    }

    @Override
    public long ramBytesUsed() {
        return Integer.SIZE / 8;
    }
}
