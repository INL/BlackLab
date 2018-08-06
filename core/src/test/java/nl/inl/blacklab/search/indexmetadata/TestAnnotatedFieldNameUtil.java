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
import org.junit.Before;
import org.junit.Test;

public class TestAnnotatedFieldNameUtil {

    //private boolean oldFieldNameSetting;

    @Test
    public void testIsAlternative() {
        String fieldName;
        fieldName = AnnotatedFieldNameUtil.propertyField("field", "property");
        Assert.assertEquals(true, AnnotatedFieldNameUtil.isAlternative(fieldName, ""));
        Assert.assertEquals(false, AnnotatedFieldNameUtil.isAlternative(fieldName, "property"));
        Assert.assertEquals(false, AnnotatedFieldNameUtil.isAlternative(fieldName, "field"));

        fieldName = AnnotatedFieldNameUtil.propertyField("field", "property", "alternative");
        Assert.assertEquals(true, AnnotatedFieldNameUtil.isAlternative(fieldName, "alternative"));
        Assert.assertEquals(false, AnnotatedFieldNameUtil.isAlternative(fieldName, "property"));
        Assert.assertEquals(false, AnnotatedFieldNameUtil.isAlternative(fieldName, "field"));
    }

    @Test
    public void testGetBaseName() {
        String fieldName;
        fieldName = AnnotatedFieldNameUtil.propertyField("field", "property");
        Assert.assertEquals("field", AnnotatedFieldNameUtil.getBaseName(fieldName));

        fieldName = AnnotatedFieldNameUtil.propertyField("field", "property", "alternative");
        Assert.assertEquals("field", AnnotatedFieldNameUtil.getBaseName(fieldName));
    }

    @Test
    public void testComplexFieldName() {
        Assert.assertEquals("field" + AnnotatedFieldNameUtil.PROP_SEP + "property",
                AnnotatedFieldNameUtil.propertyField("field", "property"));
        Assert.assertEquals("field" + AnnotatedFieldNameUtil.PROP_SEP + "property"
                + AnnotatedFieldNameUtil.ALT_SEP + "alternative",
                AnnotatedFieldNameUtil.propertyField("field", "property", "alternative"));
        Assert.assertEquals("test" + AnnotatedFieldNameUtil.PROP_SEP + "word" + AnnotatedFieldNameUtil.ALT_SEP + "s",
                AnnotatedFieldNameUtil.propertyField("test", "word", "s"));
        Assert.assertEquals("hw" + AnnotatedFieldNameUtil.ALT_SEP + "s",
                AnnotatedFieldNameUtil.propertyField(null, "hw", "s"));
    }

    public void testArray(String[] expected, String[] actual) {
        Assert.assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals(expected[i], actual[i]);
        }
    }

    @Before
    public void setUp() {
        //oldFieldNameSetting = ComplexFieldUtil.usingOldFieldNames();
    }

    @Test
    public void testGetNameComponents() {
        //testArray(new String[] { "contents" },
        //		ComplexFieldUtil.getNameComponents(ComplexFieldUtil.propertyField("contents", null, null)));
        testArray(new String[] { "contents", "lemma" },
                AnnotatedFieldNameUtil.getNameComponents(AnnotatedFieldNameUtil.propertyField("contents", "lemma", null)));
        testArray(new String[] { "contents", "lemma", "s" },
                AnnotatedFieldNameUtil.getNameComponents(AnnotatedFieldNameUtil.propertyField("contents", "lemma", "s")));

        testArray(new String[] { "contents", null, null, "cid" },
                AnnotatedFieldNameUtil.getNameComponents(AnnotatedFieldNameUtil.bookkeepingField("contents", null, "cid")));
        testArray(new String[] { "contents", "lemma", null, "fiid" },
                AnnotatedFieldNameUtil.getNameComponents(AnnotatedFieldNameUtil.bookkeepingField("contents", "lemma", "fiid")));

    }
}
