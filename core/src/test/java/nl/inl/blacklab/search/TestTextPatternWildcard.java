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
package nl.inl.blacklab.search;

import org.junit.Test;

public class TestTextPatternWildcard {

    @Test
    public void testBla() {
        // OK
    }

//	@Test
//	public void testGetAppropriatePatternSimple() {
//		TextPattern t = TextPatternWildcard.getAppropriatePattern("blabla");
//		Assert.assertEquals(TextPatternTerm.class, t.getClass());
//		Assert.assertEquals("blabla", ((TextPatternTerm) t).getValue());
//	}
//
//	@Test
//	public void testGetAppropriatePatternPrefix() {
//		TextPattern t = TextPatternWildcard.getAppropriatePattern("blabla*");
//		Assert.assertEquals(TextPatternPrefix.class, t.getClass());
//		Assert.assertEquals("blabla", ((TextPatternPrefix) t).getValue());
//	}
//
//	@Test
//	public void testGetAppropriatePatternWildcard() {
//		TextPattern t = TextPatternWildcard.getAppropriatePattern("*bla");
//		Assert.assertEquals(TextPatternWildcard.class, t.getClass());
//		Assert.assertEquals("*bla", ((TextPatternWildcard) t).getValue());
//
//		t = TextPatternWildcard.getAppropriatePattern("*bl??a*");
//		Assert.assertEquals(TextPatternWildcard.class, t.getClass());
//		Assert.assertEquals("*bl??a*", ((TextPatternWildcard) t).getValue());
//	}

}
