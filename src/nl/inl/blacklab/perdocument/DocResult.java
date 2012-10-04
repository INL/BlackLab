/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
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

	public void copyConcordanceStatus(Hits source) {
		hits.setConcordanceStatus(source.getConcordanceField(), source.getConcordanceType());
	}

}
