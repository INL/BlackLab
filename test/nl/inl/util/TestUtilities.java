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
package nl.inl.util;

import junit.framework.Assert;

import org.junit.Test;

public class TestUtilities {
	@Test
	public void testReverseWordsInString() {
		Assert.assertEquals("eet bananen geen die aap Een",
				Utilities.reverseWordsInString("Een aap die geen bananen eet"));
	}

	@Test
	public void testSanitizeForSorting() {
		// Remove accents and punctuation
		Assert.assertEquals("He jij", Utilities.sanitizeForSorting("Hé, jij!"));

		// Keep numbers
		Assert.assertEquals("De 123 test", Utilities.sanitizeForSorting("De 123 test"));

		// Strip leading space
		Assert.assertEquals("Leading", Utilities.sanitizeForSorting(" Leading"));

		// Strip trailing space
		Assert.assertEquals("Trailing", Utilities.sanitizeForSorting("Trailing "));

	}

	@Test
	public void testXmlToSortable() {
		// Don't lowercase
		Assert.assertEquals("TEST test", Utilities.xmlToSortable("TEST test", false));

		// Do lowercase
		Assert.assertEquals("test test", Utilities.xmlToSortable("TEST test", true));

		// Remove tags
		Assert.assertEquals("test test test",
				Utilities.xmlToSortable("test <bla>test</bla> test", true));

		// Interpret entities (and remove punctuation)
		Assert.assertEquals("test test", Utilities.xmlToSortable("test &gt; test", true));

		// Interpret numerical entities (and lowercase)
		Assert.assertEquals("test a test", Utilities.xmlToSortable("test &#65; test", true));

		// Interpret hex numerical entities (no lowercase)
		Assert.assertEquals("test B test", Utilities.xmlToSortable("test &#x42; test", false));

		// Ignore entities inside tags
		Assert.assertEquals("test test",
				Utilities.xmlToSortable("test <bla test=\"&quot;\" > test", true));

		// Other whitespace characters normalized to space
		Assert.assertEquals("test test", Utilities.xmlToSortable("test\ntest", true));

		// Normalize whitespace, strip leading
		Assert.assertEquals("test test", Utilities.xmlToSortable("\t\ttest \n\rtest", true));

		// Remove accents and punctuation
		Assert.assertEquals("He jij", Utilities.xmlToSortable("Hé, jij!", false));

		// Keep numbers
		Assert.assertEquals("De 123 test", Utilities.xmlToSortable("De 123 test", false));

		// Strip leading space
		Assert.assertEquals("Leading", Utilities.xmlToSortable(" Leading", false));

		// Strip trailing space
		Assert.assertEquals("Trailing", Utilities.xmlToSortable("Trailing ", false));
	}

}
