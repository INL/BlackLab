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

import java.util.List;

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

	/**
	 * Get all the hits in the document
	 * @return all hits in the document
	 * @deprecated inefficient when making concordances. Use getNumberOfHits() or
	 *   getHits(int max) to retrieve some or all of the hits as needed
	 */
	@Deprecated
	public Hits getHits() {
		return hits;
	}

	/**
	 * Get the number of hits in this document.
	 * @return the number of hits in the document
	 */
	public int getNumberOfHits() {
		return hits.size();
	}

	/**
	 * Get some or all hits in this document.
	 * @param max the maximum number of hits we want, or 0 if we want all hits.
	 *   Only use 0 if you really want all the hits. For example, if making concordances,
	 *   it is more efficient to retrieve only some of the hits, so BlackLab won't
	 *   fetch context for all the hits, even the ones you don't want concordances for.
	 * @return the hits
	 */
	public Hits getHits(int max) {
		if (max <= 0)
			return hits;
		return hits.window(0, max);
	}

	public int getDocId() {
		return docId;
	}

	public float getScore() {
		return score;
	}

	public void setContextField(List<String> contextField) {
		hits.setContextField(contextField);
	}

}
