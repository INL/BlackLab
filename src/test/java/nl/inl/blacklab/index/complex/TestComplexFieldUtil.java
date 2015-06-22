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

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class TestComplexFieldUtil {

	//private boolean oldFieldNameSetting;

	@Test
	public void testIsAlternative() {
		String fieldName;
		fieldName = ComplexFieldUtil.propertyField("field", "property");
		Assert.assertEquals(true, ComplexFieldUtil.isAlternative(fieldName, ""));
		Assert.assertEquals(false, ComplexFieldUtil.isAlternative(fieldName, "property"));
		Assert.assertEquals(false, ComplexFieldUtil.isAlternative(fieldName, "field"));

		fieldName = ComplexFieldUtil.propertyField("field", "property", "alternative");
		Assert.assertEquals(true, ComplexFieldUtil.isAlternative(fieldName, "alternative"));
		Assert.assertEquals(false, ComplexFieldUtil.isAlternative(fieldName, "property"));
		Assert.assertEquals(false, ComplexFieldUtil.isAlternative(fieldName, "field"));
	}

	@Test
	public void testGetBaseName() {
		String fieldName;
		fieldName = ComplexFieldUtil.propertyField("field", "property");
		Assert.assertEquals("field", ComplexFieldUtil.getBaseName(fieldName));

		fieldName = ComplexFieldUtil.propertyField("field", "property", "alternative");
		Assert.assertEquals("field", ComplexFieldUtil.getBaseName(fieldName));
	}

	@Test
	public void testComplexFieldName() {
		Assert.assertEquals("field" + ComplexFieldUtil.PROP_SEP + "property",
				ComplexFieldUtil.propertyField("field", "property"));
		Assert.assertEquals("field" + ComplexFieldUtil.PROP_SEP + "property"
				+ ComplexFieldUtil.ALT_SEP + "alternative",
				ComplexFieldUtil.propertyField("field", "property", "alternative"));
		Assert.assertEquals("test" + ComplexFieldUtil.PROP_SEP + "word" + ComplexFieldUtil.ALT_SEP + "s",
				ComplexFieldUtil.propertyField("test", "word", "s"));
		Assert.assertEquals("hw" + ComplexFieldUtil.ALT_SEP + "s",
				ComplexFieldUtil.propertyField(null, "hw", "s"));
	}

	public void testArray(String[] expected, String[] actual) {
		Assert.assertEquals(expected.length, actual.length);
		for (int i = 0; i < expected.length; i++) {
			Assert.assertEquals(expected[i], actual[i]);
		}
	}

	@Before
	public void setup() {
		//oldFieldNameSetting = ComplexFieldUtil.usingOldFieldNames();
	}

	@After
	public void shutdown() {
		ComplexFieldUtil.setFieldNameSeparators(false);
	}

	@Test
	public void testGetNameComponents() {
		ComplexFieldUtil.setFieldNameSeparators(false);
		//testArray(new String[] { "contents" },
		//		ComplexFieldUtil.getNameComponents(ComplexFieldUtil.propertyField("contents", null, null)));
		testArray(new String[] { "contents", "lemma" },
				ComplexFieldUtil.getNameComponents(ComplexFieldUtil.propertyField("contents", "lemma", null)));
		testArray(new String[] { "contents", "lemma", "s" },
				ComplexFieldUtil.getNameComponents(ComplexFieldUtil.propertyField("contents", "lemma", "s")));

		testArray(new String[] { "contents", null, null, "cid" },
				ComplexFieldUtil.getNameComponents(ComplexFieldUtil.bookkeepingField("contents", null, "cid")));
		testArray(new String[] { "contents", "lemma", null, "fiid" },
				ComplexFieldUtil.getNameComponents(ComplexFieldUtil.bookkeepingField("contents", "lemma", "fiid")));

	}
}
