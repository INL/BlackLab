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

/**
 * Wrap a Spans to retrieve hits per document, so we can process all matches in
 * a document efficiently.
 *
 * Hits are sorted by the given comparator.
 */
class SpansInBucketsPerDocumentSorted extends SpansInBucketsPerDocument {
    private boolean sortByStartPoint;

    public SpansInBucketsPerDocumentSorted(BLSpans source, boolean sortByStartPoint) {
        super(source);
        this.sortByStartPoint = sortByStartPoint;
    }

    @Override
    protected void gatherHits() throws IOException {
        super.gatherHits();
        sortHits(sortByStartPoint);
    }

}
