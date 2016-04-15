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

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

import org.apache.lucene.index.Term;
import org.junit.Assert;
import org.junit.Test;

public class TestSpanQueryAnd {

	@Test(expected = RuntimeException.class)
	public void testFieldMismatch() {
		BLSpanTermQuery first = new BLSpanTermQuery(new Term("author", "bla"));
		BLSpanTermQuery second = new BLSpanTermQuery(new Term("contents", "bla"));

		// Different fields; will throw exception
		new SpanQueryAnd(first, second);
	}

	@Test
	public void testComplexFieldDifferentProperties() {
		BLSpanTermQuery first = new BLSpanTermQuery(new Term(ComplexFieldUtil.propertyField("contents",
				"prop1"), "bla"));
		BLSpanTermQuery second = new BLSpanTermQuery(new Term(ComplexFieldUtil.propertyField("contents",
				"prop2"), "bla"));

		// No exception here because both are properties of complex field "field"
		SpanQueryAnd q = new SpanQueryAnd(first, second);

		// getField() will produce "base field name" of complex field
		Assert.assertEquals("contents", q.getField());
	}

}
