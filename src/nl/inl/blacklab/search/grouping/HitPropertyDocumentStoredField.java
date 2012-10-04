/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.search.Hit;
import nl.inl.blacklab.search.Searcher;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;

/**
 * A hit property for grouping on a stored field in the corresponding Lucene document.
 */
public class HitPropertyDocumentStoredField extends HitProperty {
	IndexReader reader;

	String fieldName;

	private String friendlyName;

	public HitPropertyDocumentStoredField(String fieldName, Searcher searcher) {
		this(fieldName, fieldName, searcher);
	}

	public HitPropertyDocumentStoredField(String fieldName, String friendlyName, Searcher searcher) {
		reader = searcher.getIndexReader();
		this.fieldName = fieldName;
		this.friendlyName = friendlyName;
	}

	@Override
	public String get(Hit result) {
		try {
			Document d = reader.document(result.doc);
			String value = d.get(fieldName);
			if (value == null)
				return "";
			return value;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getName() {
		return friendlyName;
	}
}
