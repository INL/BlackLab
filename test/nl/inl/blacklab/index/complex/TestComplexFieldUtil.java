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
package nl.inl.blacklab.index.complex;

import junit.framework.Assert;

import org.junit.Test;

public class TestComplexFieldUtil {
	@Test
	public void testIsProperty() {
		String fieldName = ComplexFieldUtil.fieldName("field", "property");
		Assert.assertEquals(true, ComplexFieldUtil.isProperty(fieldName, "property"));
		Assert.assertEquals(false, ComplexFieldUtil.isProperty(fieldName, ""));
		Assert.assertEquals(false, ComplexFieldUtil.isProperty(fieldName, "field"));

		fieldName = ComplexFieldUtil.fieldName("field", "property", "alternative");
		Assert.assertEquals(true, ComplexFieldUtil.isProperty(fieldName, "property"));
		Assert.assertEquals(false, ComplexFieldUtil.isProperty(fieldName, ""));
		Assert.assertEquals(false, ComplexFieldUtil.isProperty(fieldName, "alternative"));
	}

	@Test
	public void testIsAlternative() {
		String fieldName = ComplexFieldUtil.fieldName("field", "property");
		Assert.assertEquals(true, ComplexFieldUtil.isAlternative(fieldName, ""));
		// Assert.assertEquals(false, ComplexFieldUtil.isAlternative(fieldName, "property"));
		Assert.assertEquals(false, ComplexFieldUtil.isAlternative(fieldName, "field"));

		fieldName = ComplexFieldUtil.fieldName("field", "property", "alternative");
		Assert.assertEquals(true, ComplexFieldUtil.isAlternative(fieldName, "alternative"));
		Assert.assertEquals(false, ComplexFieldUtil.isAlternative(fieldName, "property"));
		Assert.assertEquals(false, ComplexFieldUtil.isAlternative(fieldName, "field"));
	}

	@Test
	public void testGetBaseName() {
		String fieldName = ComplexFieldUtil.fieldName("field", "property");
		Assert.assertEquals("field", ComplexFieldUtil.getBaseName(fieldName));

		fieldName = ComplexFieldUtil.fieldName("field", "property", "alternative");
		Assert.assertEquals("field", ComplexFieldUtil.getBaseName(fieldName));
	}

	@Test
	public void testComplexFieldName() {
		Assert.assertEquals("field" + ComplexFieldUtil.PROP_SEP + "property",
				ComplexFieldUtil.fieldName("field", "property"));
		Assert.assertEquals("field" + ComplexFieldUtil.PROP_SEP + "property"
				+ ComplexFieldUtil.ALT_SEP + "alternative",
				ComplexFieldUtil.fieldName("field", "property", "alternative"));
		Assert.assertEquals("test" + ComplexFieldUtil.ALT_SEP + "s",
				ComplexFieldUtil.fieldName("test", null, "s"));
		Assert.assertEquals("hw" + ComplexFieldUtil.ALT_SEP + "s",
				ComplexFieldUtil.fieldName(null, "hw", "s"));
	}

	public void testArray(String[] expected, String[] actual) {
		Assert.assertEquals(expected.length, actual.length);
		for (int i = 0; i < expected.length; i++) {
			Assert.assertEquals(expected[i], actual[i]);
		}
	}

	@Test
	public void testSplit() {
		testArray(new String[] { "contents", "", "" },
				ComplexFieldUtil.split(ComplexFieldUtil.fieldName("contents", null)));
		testArray(new String[] { "contents", "lemma", "" },
				ComplexFieldUtil.split(ComplexFieldUtil.fieldName("contents", "lemma")));
		testArray(new String[] { "contents", "lemma", "s" },
				ComplexFieldUtil.split(ComplexFieldUtil.fieldName("contents", "lemma", "s")));
		testArray(new String[] { "contents", "", "s" },
				ComplexFieldUtil.split(ComplexFieldUtil.fieldName("contents", null, "s")));
	}
}
