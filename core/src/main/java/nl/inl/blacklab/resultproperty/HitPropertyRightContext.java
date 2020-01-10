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

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Results;

/**
 * A hit property for grouping on the context of the hit. Requires
 * HitConcordances as input (so we have the hit text available).
 */
public class HitPropertyRightContext extends HitPropertyContextBase {

    static HitPropertyRightContext deserializeProp(BlackLabIndex index, AnnotatedField field, String info) {
        return deserializeProp(HitPropertyRightContext.class, index, field, info);
    }

    HitPropertyRightContext(HitPropertyRightContext prop, Results<Hit> hits, Contexts contexts, boolean invert) {
        super(prop, hits, contexts, invert);
    }

    public HitPropertyRightContext(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, ContextSize contextSize) {
        super("right context", "right", index, annotation, sensitivity, contextSize);
    }

    public HitPropertyRightContext(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        this(index, annotation, sensitivity, null);
    }

    public HitPropertyRightContext(BlackLabIndex index, Annotation annotation) {
        this(index, annotation, null, null);
    }

    public HitPropertyRightContext(BlackLabIndex index, MatchSensitivity sensitivity) {
        this(index, null, sensitivity, null);
    }

    public HitPropertyRightContext(BlackLabIndex index) {
        this(index, null, null, null);
    }

    @Override
    public HitProperty copyWith(Results<Hit> newHits, Contexts contexts, boolean invert) {
        return new HitPropertyRightContext(this, newHits, contexts, invert);
    }

    @Override
    public PropertyValueContextWords get(Hit result) {
        int[] context = contexts.get(result);
        //int contextHitStart = context[Contexts.CONTEXTS_HIT_START_INDEX];
        int contextRightStart = context[Contexts.RIGHT_START_INDEX];
        int contextLength = context[Contexts.LENGTH_INDEX];

        // Copy the desired part of the context
        int n = contextLength - contextRightStart;
        if (n <= 0)
            return new PropertyValueContextWords(index, annotation, sensitivity, new int[0], false);
        int[] dest = new int[n];
        int contextStart = contextLength * contextIndices.get(0) + Contexts.NUMBER_OF_BOOKKEEPING_INTS;
        System.arraycopy(context, contextStart + contextRightStart, dest, 0, n);
        return new PropertyValueContextWords(index, annotation, sensitivity, dest, false);
    }

    @Override
    public int compare(Hit a, Hit b) {
        int[] ca = contexts.get(a);
        int caRightStart = ca[Contexts.RIGHT_START_INDEX];
        int caLength = ca[Contexts.LENGTH_INDEX];
        int[] cb = contexts.get(b);
        int cbRightStart = cb[Contexts.RIGHT_START_INDEX];
        int cbLength = cb[Contexts.LENGTH_INDEX];

        // Compare the right context for these two hits
        int contextIndex = contextIndices.get(0);
        int ai = caRightStart;
        int bi = cbRightStart;
        while (ai < caLength && bi < cbLength) {
            int cmp = terms.compareSortPosition(
                    ca[contextIndex * caLength + ai + Contexts.NUMBER_OF_BOOKKEEPING_INTS],
                    cb[contextIndex * cbLength + bi + Contexts.NUMBER_OF_BOOKKEEPING_INTS], sensitivity);
            if (cmp != 0)
                return reverse ? -cmp : cmp;
            ai++;
            bi++;
        }
        // One or both ran out, and so far, they're equal.
        if (ai >= caLength) {
            if (bi < cbLength) {
                // b longer than a => a < b
                return reverse ? 1 : -1;
            }
            return 0; // same length; a == b
        }
        return reverse ? -1 : 1; // a longer than b => a > b
    }

}
