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

import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

public class TestStringUtil {
	@Test
	public void testJoin() {
		Assert.assertEquals("aap noot mies",
				StringUtil.join(Arrays.asList("aap", "noot", "mies"), " "));
		Assert.assertEquals("aap", StringUtil.join(Arrays.asList("aap"), " "));
		Assert.assertEquals("", StringUtil.join(new ArrayList<String>(), " "));
	}

	@Test
	public void testRemoveAccents() {
		Assert.assertEquals("He, jij!", StringUtil.removeAccents("Hé, jij!"));
	}

	@Test
	public void testEscapeXmlChars() {
		Assert.assertEquals("Test &lt; &amp; &gt; &quot; test",
				StringUtil.escapeXmlChars("Test < & > \" test"));
	}

	@Test
	public void testCapitalize() {
		Assert.assertEquals("Aap", StringUtils.capitalize("aap"));
		Assert.assertEquals("AAP", StringUtils.capitalize("AAP"));
		Assert.assertEquals("'aap'", StringUtils.capitalize("'aap'"));
	}

}
