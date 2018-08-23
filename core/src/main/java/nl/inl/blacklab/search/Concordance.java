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

import nl.inl.util.XmlUtil;

/**
 * A concordance (left context, hit text, right context). Hits class matches
 * this to the Hit.
 */
public class Concordance {

    /** Document fragment to use to create concordance */
    private String fragment;

    /** Where in content the match starts */
    private int matchStart;

    /** Where in content the match ends */
    private int matchEnd;

    /**
     * Construct a concordance.
     *
     * @param conc array containing left part, match part and right part of the
     *            concordance
     */
    public Concordance(String[] conc) {
        fragment = conc[0] + conc[1] + conc[2];
        matchStart = conc[0].length();
        matchEnd = matchStart + conc[1].length();
    }

    /**
     * Construct a concordance.
     *
     * Note that if it not guaranteed that each parts will be well-formed XML; if
     * you require that, you should use XmlHighlighter to do this yourself.
     *
     * @param contents part of the document content to use as concordance
     * @param matchStart where the match starts
     * @param matchEnd where the match ends
     */
    public Concordance(String contents, int matchStart, int matchEnd) {
        fragment = contents;
        this.matchStart = matchStart;
        this.matchEnd = matchEnd;
    }

    @Override
    public String toString() {
        return String.format("conc: %s[%s]%s", left(), match(), right());
    }

    /**
     * Return the part of the content to the left of the matched part.
     * 
     * @return the left context
     */
    public String left() {
        return fragment.substring(0, matchStart);
    }

    /**
     * Return the matched part of the content.
     * 
     * @return the matched text.
     */
    public String match() {
        return fragment.substring(matchStart, matchEnd);
    }

    /**
     * Return the part of the content to the right of the matched part.
     * 
     * @return the right context
     */
    public String right() {
        return fragment.substring(matchEnd);
    }

    /**
     * Return the three parts of the concordance: left, middle and right.
     * 
     * @return an array of concordance parts
     */
    public String[] parts() {
        return new String[] { left(), match(), right() };
    }

    /**
     * Return the parts of the concordance with XML tags stripped out.
     *
     * @return an array of three Strings: the left context, the matched text, and
     *         the right context.
     */
    public String[] partsNoXml() {
        String leftContext = XmlUtil.xmlToPlainText(left());
        String matchedWords = XmlUtil.xmlToPlainText(match());
        String rightContext = XmlUtil.xmlToPlainText(right());
        return new String[] { leftContext, matchedWords, rightContext };
    }

}
