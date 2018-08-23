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

import org.apache.lucene.search.spans.Spans;

/**
 * Wrap a Spans to retrieve matches per document, so we can process all matches
 * in a document efficiently.
 *
 * This way we can retrieve hits per document and perform some operation on them
 * (like sorting or retrieving some extra information). Afterwards we can use
 * HitsPerDocumentSpans to convert the per-document hits into a normal Spans
 * object again.
 */
class SpansInBucketsPerDocument extends SpansInBucketsAbstract {
    public SpansInBucketsPerDocument(BLSpans source) {
        super(source);
    }

    @Override
    protected void gatherHits() throws IOException {
        do {
            addHitFromSource();
        } while (source.nextStartPosition() != Spans.NO_MORE_POSITIONS);
    }

}
