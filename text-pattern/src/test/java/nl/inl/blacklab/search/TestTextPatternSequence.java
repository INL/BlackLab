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
import nl.inl.blacklab.search.textpattern.TextPatternAnyToken;
import nl.inl.blacklab.search.textpattern.TextPatternSequence;
import nl.inl.blacklab.search.textpattern.TextPatternTerm;

public class TestTextPatternSequence {

    @Test
    public void testSequence() {
        // "the" followed by "fox", with 1-3 tokens in between
        TextPattern seq = new TextPatternSequence(new TextPatternTerm("the"), new TextPatternTerm(
                "fox"));

        Assert.assertEquals("SEQ(TERM(the), TERM(fox))", seq.toString());
    }

    @Test
    public void testSequenceAnyMiddle() {
        // "the" followed by "fox", with 1-3 tokens in between
        TextPattern seq = new TextPatternSequence(new TextPatternTerm("the"),
                new TextPatternAnyToken(1, 3), new TextPatternTerm("fox"));

        Assert.assertEquals("SEQ(TERM(the), ANYTOKEN(1, 3), TERM(fox))", seq.toString());
    }

    @Test
    public void testSequenceAnyRight() {
        // "the" followed by "fox", with 1-3 tokens in between
        TextPattern seq = new TextPatternSequence(new TextPatternTerm("the"),
                new TextPatternAnyToken(1, 3));

        Assert.assertEquals("SEQ(TERM(the), ANYTOKEN(1, 3))", seq.toString());
    }

    @Test
    public void testSequenceAnyLeft() {
        // "the" followed by "fox", with 1-3 tokens in between
        TextPattern seq = new TextPatternSequence(new TextPatternAnyToken(1, 3),
                new TextPatternTerm("fox"));

        Assert.assertEquals("SEQ(ANYTOKEN(1, 3), TERM(fox))", seq.toString());
    }

}
