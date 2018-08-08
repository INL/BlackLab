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
package nl.inl.blacklab.resultproperty;

import java.util.Arrays;
import java.util.List;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping on the context of the hit. Requires
 * HitConcordances as input (so we have the hit text available).
 */
public class HitPropertyWordLeft extends HitProperty {

    private String luceneFieldName;

    private Annotation annotation;

    private Terms terms;

    private boolean sensitive;

    private BlackLabIndex searcher;

    public HitPropertyWordLeft(Hits hits, Annotation annotation) {
        this(hits, annotation, hits.index().defaultMatchSensitivity().isCaseSensitive());
    }

    public HitPropertyWordLeft(Hits hits, AnnotatedField field) {
        this(hits, field.annotations().main(), hits.index().defaultMatchSensitivity().isCaseSensitive());
    }

    public HitPropertyWordLeft(Hits hits) {
        this(hits, hits.index().mainAnnotatedField(), hits.index().defaultMatchSensitivity().isCaseSensitive());
    }

    public HitPropertyWordLeft(Hits hits, Annotation annotation, boolean sensitive) {
        super(hits);
        this.searcher = hits.index();
        this.luceneFieldName = annotation.luceneFieldPrefix();
        this.annotation = annotation;
        this.terms = searcher.terms(annotation);
        this.sensitive = sensitive;
    }

    public HitPropertyWordLeft(Hits hits, AnnotatedField field, boolean sensitive) {
        this(hits, field.annotations().main(), sensitive);
    }

    public HitPropertyWordLeft(Hits hits, boolean sensitive) {
        this(hits, hits.index().mainAnnotatedField(), sensitive);
    }

    @Override
    public HitPropValueContextWord get(int hitNumber) {
        int[] context = hits.getHitContext(hitNumber);
        int contextHitStart = context[Hits.CONTEXTS_HIT_START_INDEX];
        //int contextRightStart = context[Hits.CONTEXTS_RIGHT_START_INDEX];
        int contextLength = context[Hits.CONTEXTS_LENGTH_INDEX];

        if (contextHitStart <= 0)
            return new HitPropValueContextWord(hits, annotation, -1, sensitive);
        int contextStart = contextLength * contextIndices.get(0) + Hits.CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS;
        return new HitPropValueContextWord(hits, annotation, context[contextStart
                + contextHitStart - 1], sensitive);
    }

    @Override
    public int compare(Object i, Object j) {
        int[] ca = hits.getHitContext((Integer) i);
        int caHitStart = ca[Hits.CONTEXTS_HIT_START_INDEX];
        int caLength = ca[Hits.CONTEXTS_LENGTH_INDEX];
        int[] cb = hits.getHitContext((Integer) j);
        int cbHitStart = cb[Hits.CONTEXTS_HIT_START_INDEX];
        int cbLength = cb[Hits.CONTEXTS_LENGTH_INDEX];

        if (caHitStart <= 0)
            return cbHitStart <= 0 ? 0 : (reverse ? 1 : -1);
        if (cbHitStart <= 0)
            return reverse ? -1 : 1;
        // Compare one word to the left of the hit
        int contextIndex = contextIndices.get(0);

        int cmp = terms.compareSortPosition(
                ca[contextIndex * caLength + caHitStart - 1 + Hits.CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS],
                cb[contextIndex * cbLength + cbHitStart - 1 + Hits.CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS],
                sensitive);
        return reverse ? -cmp : cmp;
    }

    @Override
    public List<Annotation> needsContext() {
        return Arrays.asList(annotation);
    }

    @Override
    public String getName() {
        return "word left";
    }

    @Override
    public List<String> getPropNames() {
        return Arrays.asList("word left: " + annotation.name());
    }

    @Override
    public String serialize() {
        String[] parts = AnnotatedFieldNameUtil.getNameComponents(luceneFieldName);
        String thePropName = parts.length > 1 ? parts[1] : "";
        return serializeReverse() + PropValSerializeUtil.combineParts("wordleft", thePropName, sensitive ? "s" : "i");
    }

    public static HitPropertyWordLeft deserialize(Hits hits, String info) {
        String[] parts = PropValSerializeUtil.splitParts(info);
        AnnotatedField field = hits.field();
        String propName = parts[0];
        if (propName.length() == 0)
            propName = AnnotatedFieldNameUtil.getDefaultMainAnnotationName();
        boolean sensitive = parts.length > 1 ? parts[1].equalsIgnoreCase("s") : true;
        Annotation annotation = field.annotations().get(propName);
        return new HitPropertyWordLeft(hits, annotation, sensitive);
    }

}
