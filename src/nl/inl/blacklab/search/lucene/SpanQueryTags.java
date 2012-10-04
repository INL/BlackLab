/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Collection;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.spans.SpanQuery;
import org.apache.lucene.search.spans.Spans;

/**
 *
 * Returns spans corresponding to a certain element (tag) type.
 *
 * For example, SpanQueryTags("ne") will give us spans for all the <ne> elements in the document.
 *
 * NOTE: this does not work with nested tags yet, as it just searches for all open and close tags
 * and matches the first open tag with the first close tag, etc.
 *
 */
public class SpanQueryTags extends SpanQueryBase {
	private String tagName;

	public SpanQueryTags(String complexFieldName, String tagName) {
		super();
		this.tagName = tagName;
		clauses = new SpanQuery[2];
		baseFieldName = complexFieldName;
		String startTagFieldName = ComplexFieldUtil.fieldName(complexFieldName, "starttag");
		String endTagFieldName = ComplexFieldUtil.fieldName(complexFieldName, "endtag");

		// Use a BlackLabSpanTermQuery instead of default Lucene one
		// because we need to override getField() to only return the base field name,
		// not the complete field name with the property.
		clauses[0] = new BLSpanTermQuery(new Term(startTagFieldName, tagName));
		clauses[1] = new BLSpanTermQuery(new Term(endTagFieldName, tagName));
	}

	public SpanQueryTags(Collection<SpanQuery> clauscol) {
		super(clauscol);
	}

	public SpanQueryTags(SpanQuery[] _clauses) {
		super(_clauses);
	}

	@Override
	public Spans getSpans(IndexReader reader) throws IOException {
		Spans startTags = clauses[0].getSpans(reader);
		Spans endTags = clauses[1].getSpans(reader);
		return new SpansTags(startTags, endTags);
	}

	@Override
	public String toString(String field) {
		return "SpanQueryTags(" + tagName + ")";
	}
}
