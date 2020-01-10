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

import java.util.AbstractList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.text.StringEscapeUtils;

import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * A "keyword in context" for a hit (left context, hit text, right context).
 *
 * The Hits class matches this to the Hit.
 *
 * This object may be converted to a Concordance object (with XML strings) by
 * calling Kwic.toConcordance().
 */
public class Kwic {

    private DocContentsFromForwardIndex fragment;

    private int hitStart;

    private int hitEnd;

    public static final String DEFAULT_CONC_PUNCT_PROP = AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME;

    public static final String DEFAULT_CONC_WORD_PROP = AnnotatedFieldNameUtil.WORD_ANNOT_NAME;

    /**
     * Construct a Kwic object
     *
     * @param annotations What annotations are stored in what order for this Kwic
     *            (e.g. word, lemma, pos)
     * @param tokens the contents
     * @param matchStart where the match starts, in word positions
     * @param matchEnd where the match ends, in word positions
     */
    public Kwic(List<Annotation> annotations, List<String> tokens, int matchStart, int matchEnd) {
        fragment = new DocContentsFromForwardIndex(annotations, tokens);
        this.hitStart = matchStart;
        this.hitEnd = matchEnd;
    }

    /**
     * Construct a Kwic object
     *
     * @param fragment the content fragment to make the Kwic from
     * @param matchStart where the match starts, in word positions
     * @param matchEnd where the match ends, in word positions
     */
    public Kwic(DocContentsFromForwardIndex fragment, int matchStart, int matchEnd) {
        this.fragment = fragment;
        this.hitStart = matchStart;
        this.hitEnd = matchEnd;
    }

    public List<String> left() {
        return Collections.unmodifiableList(fragment.tokens.subList(0, hitStart * fragment.annotations.size()));
    }

    /**
     * Get the left context of a specific annotation
     * 
     * @param annotation the annotation to get the context for
     * @return the context
     */
    public List<String> left(Annotation annotation) {
        return singlePropertyContext(annotation, 0, hitStart);
    }

    public List<String> match() {
        return Collections.unmodifiableList(
                fragment.tokens.subList(hitStart * fragment.annotations.size(), hitEnd * fragment.annotations.size()));
    }

    /**
     * Get the match context of a specific annotation
     * 
     * @param annotation the annotation to get the context for
     * @return the context
     */
    public List<String> match(Annotation annotation) {
        return singlePropertyContext(annotation, hitStart, hitEnd);
    }

    public List<String> right() {
        return Collections
                .unmodifiableList(fragment.tokens.subList(hitEnd * fragment.annotations.size(), fragment.tokens.size()));
    }

    /**
     * Get the right context of a specific annotation
     * 
     * @param annotation the annotation to get the context for
     * @return the context
     */
    public List<String> right(Annotation annotation) {
        return singlePropertyContext(annotation, hitEnd, fragment.tokens.size() / fragment.annotations.size());
    }

    /**
     * Get all the annotations of all the tokens in the hit's context fragment.
     * 
     * @return the token annotations
     */
    public List<String> tokens() {
        return fragment.tokens();
    }

    /**
     * Get all values for a single annotation for all the tokens in the hit's context
     * fragment.
     *
     * @param annotation the annotation to get
     * @return the values of this annotation for all tokens
     */
    public List<String> tokens(Annotation annotation) {
        return fragment.tokens(annotation);
    }

    /**
     * Get the context of a specific annotation from the complete context list.
     *
     * @param annotation the annotation to get the context for
     * @param start first word position to get the annotation context for
     * @param end word position after the last to get the annotation context for
     * @return the context for this annotation
     */
    private List<String> singlePropertyContext(Annotation annotation, int start, int end) {
        final int nProp = fragment.annotations.size();
        final int size = end - start;
        final int propIndex = fragment.annotations.indexOf(annotation);
        final int startIndex = start * nProp + propIndex;
        if (propIndex == -1)
            return null;
        return new AbstractList<String>() {
            @Override
            public String get(int index) {
                if (index >= size)
                    throw new IndexOutOfBoundsException();
                return fragment.tokens.get(startIndex + nProp * index);
            }

            @Override
            public int size() {
                return size;
            }
        };
    }

    /**
     * Convert this Kwic object to a Concordance object.
     *
     * This produces XML consisting of &lt;w&gt; tags. The words are the text
     * content of the tags. The punctuation is between the tags. The other
     * annotations are attributes of the tags.
     *
     * @return the Concordance object
     */
    public Concordance toConcordance() {
        return toConcordance(true);
    }

    /**
     * Convert this Kwic object to a Concordance object.
     *
     * This may either consist of only words and punctuation, or include the XML
     * tags containing the other annotations as well, depending on the parameter.
     *
     * @param produceXml if true, produces XML. If false, produces human-readable
     *            text.
     * @return the Concordance object
     */
    public Concordance toConcordance(boolean produceXml) {
        String[] conc = new String[3];
        List<String> match = match();
        String addPunctAfter = !match.isEmpty() ? match.get(0) : "";
        conc[0] = xmlString(left(), addPunctAfter, true, produceXml);
        conc[1] = xmlString(match, null, true, produceXml);
        conc[2] = xmlString(right(), null, false, produceXml);
        return new Concordance(conc);
    }

    /**
     * Convert a context List to an XML string (used for converting to a Concordance
     * object)
     *
     * @param context the context List to convert
     * @param addPunctAfter if not null, this is appended at the end of the string.
     * @param leavePunctBefore if true, no punctuation is added before the first
     *            word.
     * @param produceXml if true, produces XML with word tags. If false, produces
     *            human-readable text.
     * @return the XML string
     */
    private String xmlString(List<String> context, String addPunctAfter, boolean leavePunctBefore, boolean produceXml) {
        int valuesPerWord = fragment.annotations.size();
        int numberOfWords = context.size() / valuesPerWord;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < numberOfWords; i++) {
            int vIndex = i * valuesPerWord;
            int j = 0;
            if (i > 0 || !leavePunctBefore) {
                if (produceXml)
                    b.append(StringEscapeUtils.escapeXml10(context.get(vIndex)));
                else
                    b.append(context.get(vIndex));
            }
            if (produceXml) {
                b.append("<w");
                for (int k = 1; k < valuesPerWord - 1; k++) {
                    String name = fragment.annotations.get(k).name();
                    String value = context.get(vIndex + 1 + j);
                    b.append(" ").append(name).append("=\"").append(StringEscapeUtils.escapeXml10(value)).append("\"");
                    j++;
                }
                b.append(">");
            } else {
                // We're skipping the other annotations besides word and punct. Advance j.
                if (valuesPerWord > 2)
                    j += valuesPerWord - 2;
            }
            if (produceXml)
                b.append(StringEscapeUtils.escapeXml10(context.get(vIndex + 1 + j))).append("</w>");
            else
                b.append(context.get(vIndex + 1 + j));
        }
        if (addPunctAfter != null)
            b.append(addPunctAfter);
        return b.toString();
    }

    /**
     * Get the annotations in the order they occur in the context array.
     * 
     * @return the annotations
     */
    public List<Annotation> annotations() {
        return fragment.annotations();
    }

    /**
     * Return the index of the token after the last hit token in the context
     * fragment.
     * 
     * @return the hit end index
     */
    public int hitEnd() {
        return hitEnd;
    }

    /**
     * Return the index of the first hit token in the context fragment.
     * 
     * @return the hit start index
     */
    public int hitStart() {
        return hitStart;
    }

    public String fullXml() {
        return fragment.xml();
    }

    public DocContentsFromForwardIndex docContents() {
        return fragment;
    }

    @Override
    public String toString() {
        return toConcordance().toString();
    }

}
