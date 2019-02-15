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
package nl.inl.blacklab.search.textpattern;

import nl.inl.blacklab.exceptions.InvalidQuery;
import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * TextPattern for wrapping another TextPattern so that it applies to a certain
 * word annotation.
 *
 * For example, to find lemmas starting with "bla": <code>
 * TextPattern tp = new TextPatternProperty("lemma", new TextPatternWildcard("bla*"));
 * </code>
 */
public class TextPatternSensitive extends TextPattern {

    private TextPattern input;

    private MatchSensitivity sensitivity;

    /**
     * Indicate that we want to use a different list of alternatives for this part
     * of the query.
     * 
     * @param caseSensitive search case-sensitively?
     * @param diacriticsSensitive search diacritics-sensitively?
     * @param input
     * @deprecated use {@link #TextPatternSensitive(MatchSensitivity, TextPattern)}
     */
    @Deprecated
    public TextPatternSensitive(boolean caseSensitive, boolean diacriticsSensitive, TextPattern input) {
        this.sensitivity = MatchSensitivity.get(caseSensitive, diacriticsSensitive);
        this.input = input;
    }

    /**
     * Indicate that we want to use a different list of alternatives for this part
     * of the query.
     * 
     * @param sensitivity search case-/diacritics-sensitively?
     * @param input
     */
    public TextPatternSensitive(MatchSensitivity sensitivity, TextPattern input) {
        this.sensitivity = sensitivity;
        this.input = input;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) throws InvalidQuery {
        return input.translate(context.withSensitive(sensitivity));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternSensitive) {
            TextPatternSensitive tp = ((TextPatternSensitive) obj);
            return sensitivity == tp.sensitivity &&
                    input.equals(tp.input);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return sensitivity.hashCode() + input.hashCode();
    }

    @Override
    public String toString() {
        String sett = sensitivity.toString();
        return "SENSITIVE(" + sett + ", " + input.toString() + ")";
    }
}
