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
 * A hit property for grouping on the text actually matched. Requires
 * HitConcordances as input (so we have the hit text available).
 */
public class HitPropertyHitText extends HitProperty {

    private Terms terms;

    private boolean sensitive;

    private BlackLabIndex searcher;

    private Annotation annotation;

    public HitPropertyHitText(Hits hits, Annotation annotation) {
        this(hits, annotation, hits.index().defaultMatchSensitivity().isCaseSensitive());
    }

    public HitPropertyHitText(Hits hits, AnnotatedField field) {
        this(hits, field.annotations().main(), hits.index().defaultMatchSensitivity().isCaseSensitive());
    }

    public HitPropertyHitText(Hits hits) {
        this(hits, hits.index().mainAnnotatedField(), hits.index().defaultMatchSensitivity().isCaseSensitive());
    }

    public HitPropertyHitText(Hits hits, Annotation annotation, boolean sensitive) {
        super(hits);
        this.searcher = hits.index();
        this.annotation = annotation;
        this.terms = searcher.forwardIndex(annotation).terms();
        this.sensitive = sensitive;
    }

    public HitPropertyHitText(Hits hits, AnnotatedField field, boolean sensitive) {
        this(hits, field.annotations().main(), sensitive);
    }

    public HitPropertyHitText(Hits hits, boolean sensitive) {
        this(hits, hits.index().mainAnnotatedField(), sensitive);
    }

    @Override
    public HitPropValueContextWords get(int hitNumber) {
        int[] context = hits.getHitContext(hitNumber);
        int contextHitStart = context[Hits.CONTEXTS_HIT_START_INDEX];
        int contextRightStart = context[Hits.CONTEXTS_RIGHT_START_INDEX];
        int contextLength = context[Hits.CONTEXTS_LENGTH_INDEX];

        // Copy the desired part of the context
        int n = contextRightStart - contextHitStart;
        if (n <= 0)
            return new HitPropValueContextWords(hits, annotation, new int[0], sensitive);
        int[] dest = new int[n];
        int contextStart = contextLength * contextIndices.get(0) + Hits.CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS;
        System.arraycopy(context, contextStart + contextHitStart, dest, 0, n);
        return new HitPropValueContextWords(hits, annotation, dest, sensitive);
    }

    @Override
    public int compare(Object i, Object j) {
        int[] ca = hits.getHitContext((Integer) i);
        int caHitStart = ca[Hits.CONTEXTS_HIT_START_INDEX];
        int caRightStart = ca[Hits.CONTEXTS_RIGHT_START_INDEX];
        int caLength = ca[Hits.CONTEXTS_LENGTH_INDEX];
        int[] cb = hits.getHitContext((Integer) j);
        int cbHitStart = cb[Hits.CONTEXTS_HIT_START_INDEX];
        int cbRightStart = cb[Hits.CONTEXTS_RIGHT_START_INDEX];
        int cbLength = cb[Hits.CONTEXTS_LENGTH_INDEX];

        // Compare the hit context for these two hits
        int contextIndex = contextIndices.get(0);
        int ai = caHitStart;
        int bi = cbHitStart;
        while (ai < caRightStart && bi < cbRightStart) {
            int cmp = terms.compareSortPosition(
                    ca[contextIndex * caLength + ai + Hits.CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS],
                    cb[contextIndex * cbLength + bi + Hits.CONTEXTS_NUMBER_OF_BOOKKEEPING_INTS], sensitive);
            if (cmp != 0)
                return reverse ? -cmp : cmp;
            ai++;
            bi++;
        }
        // One or both ran out, and so far, they're equal.
        if (ai == caRightStart) {
            if (bi != cbRightStart) {
                // b longer than a => a < b
                return reverse ? 1 : -1;
            }
            return 0; // same length; a == b
        }
        return reverse ? -1 : 1; // a longer than b => a > b
    }

    @Override
    public List<Annotation> needsContext() {
        return Arrays.asList(annotation);
    }

    @Override
    public String getName() {
        return "hit text";
    }

    @Override
    public List<String> getPropNames() {
        return Arrays.asList("hit: " + annotation.name());
    }

    @Override
    public String serialize() {
        return serializeReverse() + PropValSerializeUtil.combineParts("hit", annotation.name(), sensitive ? "s" : "i");
    }

    public static HitPropertyHitText deserialize(Hits hits, String info) {
        String[] parts = PropValSerializeUtil.splitParts(info);
        AnnotatedField field = hits.field();
        String propName = parts[0];
        if (propName.length() == 0)
            propName = AnnotatedFieldNameUtil.getDefaultMainAnnotationName();
        boolean sensitive = parts.length > 1 ? parts[1].equalsIgnoreCase("s") : true;
        Annotation annotation = field.annotations().get(propName);
        return new HitPropertyHitText(hits, annotation, sensitive);
    }
}
