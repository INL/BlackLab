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
package nl.inl.blacklab.analysis;

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/**
 * Replaces punctuation with space.
 */
public class RemovePunctuationFilter extends TokenFilter {
    final static Pattern punctuationPattern = Pattern.compile("\\p{P}+");

    /**
     * Remove punctuation from a string
     * 
     * @param input the string
     * @return same string with punctuation removed
     */
    public static String process(String input) {
        return punctuationPattern.matcher(input).replaceAll("");
    }

    /**
     * Test program
     * 
     * @param args
     */
    public static void main(String[] args) {
        String input = "Hé, jij daar!";
        System.out.println(process(input));
    }

    private CharTermAttribute termAtt;

    /**
     * @param input the token stream to remove punctuation from
     */
    public RemovePunctuationFilter(TokenStream input) {
        super(input);
        termAtt = addAttribute(CharTermAttribute.class);
    }

    @Override
    final public boolean incrementToken() throws IOException {
        if (input.incrementToken()) {
            String t = new String(termAtt.buffer(), 0, termAtt.length());
            t = process(t);
            termAtt.copyBuffer(t.toCharArray(), 0, t.length());
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((termAtt == null) ? 0 : termAtt.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        RemovePunctuationFilter other = (RemovePunctuationFilter) obj;
        if (termAtt == null) {
            if (other.termAtt != null)
                return false;
        } else if (!termAtt.equals(other.termAtt))
            return false;
        return true;
    }

}
