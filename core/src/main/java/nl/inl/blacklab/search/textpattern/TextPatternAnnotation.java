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

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.lucene.BLSpanQuery;

/**
 * TextPattern for wrapping another TextPattern so that it applies to a certain
 * annotation on an annotated field.
 *
 * For example, to find lemmas starting with "bla": <code>
 * TextPattern tp = new TextPatternAnnotation("lemma", new TextPatternWildcard("bla*"));
 * </code>
 */
public class TextPatternAnnotation extends TextPattern {
    private TextPattern input;

    private String annotationName;

    public TextPatternAnnotation(String propertyName, TextPattern input) {
        this.annotationName = propertyName == null ? "" : propertyName;
        this.input = input;
    }

    @Override
    public BLSpanQuery translate(QueryExecutionContext context) {
        return input.translate(context.withProperty(annotationName));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TextPatternAnnotation) {
            TextPatternAnnotation tp = ((TextPatternAnnotation) obj);
            return input.equals(tp.input) && annotationName.equals(tp.annotationName);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return input.hashCode() + annotationName.hashCode();
    }

    @Override
    public String toString() {
        return "PROP(" + annotationName + ", " + input.toString() + ")";
    }

}
