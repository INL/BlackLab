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
package nl.inl.blacklab.perdocument;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Hits;
import nl.inl.blacklab.search.Searcher;

import org.apache.lucene.document.Document;

/**
 * A document result, containing a Lucene document from the index and a collection of Hit objects.
 */
public class DocResult {
	private int docId;

	private Document document;

	private Hits hits;

	private float score;

	public DocResult(Searcher searcher, String field, int docId, Document document) {
		this(searcher, field, docId, document, 0.0f);
	}

	public DocResult(Searcher searcher, String field, int docId, Document document, float score) {
		this.docId = docId;
		this.document = document;
		this.score = score;
		hits = new Hits(searcher, field);
	}

	/**
	 * Add a hit to the list of hits.
	 *
	 * @param hit
	 */
	public void addHit(Hit hit) {
		hits.add(hit);
	}

	public Document getDocument() {
		return document;
	}

	public Hits getHits() {
		return hits;
	}

	public int getDocId() {
		return docId;
	}

	public float getScore() {
		return score;
	}

	public void setContextField(String contextField) {
		hits.setContextField(contextField);
	}

}
