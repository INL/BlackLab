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

import org.junit.Assert;
import org.junit.Test;

import nl.inl.blacklab.search.textpattern.TextPattern;
import nl.inl.blacklab.search.textpattern.TextPatternRegex;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;

public class TestTextPatternRegex {

    @Test
    public void testEmptyPattern() {
        TextPattern r = new TextPatternRegex("");
        Assert.assertEquals("", ((TextPatternRegex) r).getValue());

        Assert.assertTrue(r instanceof TextPatternTerm);
        Assert.assertEquals("", ((TextPatternTerm) r).getValue());
    }

    @Test
    public void testBasicPattern() {
        TextPattern r = new TextPatternRegex("bla");
        Assert.assertEquals("bla", ((TextPatternRegex) r).getValue());
    }
}
