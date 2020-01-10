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
package nl.inl.blacklab.search.indexmetadata;

import org.junit.Assert;
import org.junit.Test;

public class TestAnnotatedFieldNameUtil {

    @Test
    public void testGetBaseName() {
        String fieldName;
        fieldName = AnnotatedFieldNameUtil.annotationField("field", "annotation");
        Assert.assertEquals("field", AnnotatedFieldNameUtil.getBaseName(fieldName));

        fieldName = AnnotatedFieldNameUtil.annotationField("field", "annotation", "sensitivity");
        Assert.assertEquals("field", AnnotatedFieldNameUtil.getBaseName(fieldName));
    }

    @Test
    public void testAnnotatedFieldName() {
        Assert.assertEquals("field" + AnnotatedFieldNameUtil.ANNOT_SEP + "annotation",
                AnnotatedFieldNameUtil.annotationField("field", "annotation"));
        Assert.assertEquals("field" + AnnotatedFieldNameUtil.ANNOT_SEP + "annotation"
                + AnnotatedFieldNameUtil.SENSITIVITY_SEP + "sensitivity",
                AnnotatedFieldNameUtil.annotationField("field", "annotation", "sensitivity"));
        Assert.assertEquals("test" + AnnotatedFieldNameUtil.ANNOT_SEP + "word" + AnnotatedFieldNameUtil.SENSITIVITY_SEP + "s",
                AnnotatedFieldNameUtil.annotationField("test", "word", "s"));
        Assert.assertEquals("hw" + AnnotatedFieldNameUtil.SENSITIVITY_SEP + "s",
                AnnotatedFieldNameUtil.annotationField(null, "hw", "s"));
    }

    public void testArray(String[] expected, String[] actual) {
        Assert.assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(expected[i], actual[i]);
        }
    }

    @Test
    public void testGetNameComponents() {
        testArray(new String[] { "contents", "lemma" },
                AnnotatedFieldNameUtil.getNameComponents(AnnotatedFieldNameUtil.annotationField("contents", "lemma", null)));
        testArray(new String[] { "contents", "lemma", "s" },
                AnnotatedFieldNameUtil.getNameComponents(AnnotatedFieldNameUtil.annotationField("contents", "lemma", "s")));

        testArray(new String[] { "contents", null, null, "cid" },
                AnnotatedFieldNameUtil.getNameComponents(AnnotatedFieldNameUtil.bookkeepingField("contents", null, "cid")));
        testArray(new String[] { "contents", "lemma", null, "fiid" },
                AnnotatedFieldNameUtil.getNameComponents(AnnotatedFieldNameUtil.bookkeepingField("contents", "lemma", "fiid")));

    }
    
    @Test
    public void testSanitizeXmlElementName() {
        Assert.assertEquals("word", AnnotatedFieldNameUtil.sanitizeXmlElementName("word"));
        Assert.assertEquals("_0word", AnnotatedFieldNameUtil.sanitizeXmlElementName("0word"));
        Assert.assertEquals("_xmlword", AnnotatedFieldNameUtil.sanitizeXmlElementName("xmlword"));
        Assert.assertEquals("fun_word", AnnotatedFieldNameUtil.sanitizeXmlElementName("fun-word"));
        Assert.assertEquals("fun.word", AnnotatedFieldNameUtil.sanitizeXmlElementName("fun.word"));
        Assert.assertEquals("fun_word", AnnotatedFieldNameUtil.sanitizeXmlElementName("fun_word"));
        Assert.assertEquals("word0", AnnotatedFieldNameUtil.sanitizeXmlElementName("word0"));
    }
}
