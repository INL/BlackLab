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
public class HitPropertyDocumentDecade extends HitProperty {
	IndexReader reader;

	String fieldName;

	public HitPropertyDocumentDecade(String fieldName, IndexReader reader) {
		this.reader = reader;
		this.fieldName = fieldName;
	}

	public HitPropertyDocumentDecade(String fieldName, Searcher searcher) {
		this(fieldName, searcher.getIndexReader());
	}

	@Override
	public String get(Hit result) {
		try {
			Document d = reader.document(result.doc);
			String strYear = d.get(fieldName);
			int year = Integer.parseInt(strYear);
			year -= year % 10;
			return Integer.toString(year);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getHumanReadable(Hit result) {
		try {
			Document d = reader.document(result.doc);
			String strYear = d.get(fieldName);
			int year = Integer.parseInt(strYear);
			year -= year % 10;
			return year + "-" + (year + 9);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public String getName() {
		return "decade";
	}
}
