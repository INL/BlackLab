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

import org.apache.lucene.index.Term;
import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;

public class TestSpanQueryAnd {

    @SuppressWarnings("unused")
    @Test(expected = RuntimeException.class)
    public void testFieldMismatch() {
        BLSpanTermQuery first = new BLSpanTermQuery(null, new Term("author", "bla"));
        BLSpanTermQuery second = new BLSpanTermQuery(null, new Term("contents", "bla"));

        // Different fields; will throw exception
        new SpanQueryAnd(first, second);
    }

    @Test
    public void testAnnotatedFieldDifferentProperties() {
        BLSpanTermQuery first = new BLSpanTermQuery(null, new Term(AnnotatedFieldNameUtil.annotationField("contents",
                "prop1"), "bla"));
        BLSpanTermQuery second = new BLSpanTermQuery(null, new Term(AnnotatedFieldNameUtil.annotationField("contents",
                "prop2"), "bla"));

        // No exception here because both are properties of annotated field "field"
        SpanQueryAnd q = new SpanQueryAnd(first, second);

        // getField() will produce "base field name" of annotated field
        Assert.assertEquals("contents", q.getField());
    }

}
