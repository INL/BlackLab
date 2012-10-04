/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.search.lucene;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

import org.apache.lucene.index.Term;
import org.apache.lucene.search.spans.SpanTermQuery;
import org.junit.Assert;
import org.junit.Test;

public class TestSpanQueryAnd {
	@Test(expected = RuntimeException.class)
	public void testFieldMismatch() {
		SpanTermQuery first = new SpanTermQuery(new Term("author", "bla"));
		SpanTermQuery second = new SpanTermQuery(new Term("contents", "bla"));

		// Different fields; will throw exception
		new SpanQueryAnd(first, second);
	}

	@Test
	public void testComplexFieldDifferentProperties() {
		SpanTermQuery first = new SpanTermQuery(new Term(ComplexFieldUtil.fieldName("contents",
				"prop1"), "bla"));
		SpanTermQuery second = new SpanTermQuery(new Term(ComplexFieldUtil.fieldName("contents",
				"prop2"), "bla"));

		// No exception here because both are properties of complex field "field"
		SpanQueryAnd q = new SpanQueryAnd(first, second);

		// getField() will produce "base field name" of complex field
		Assert.assertEquals("contents", q.getField());
	}

}
