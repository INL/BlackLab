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
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.results.ContextSize;
import nl.inl.blacklab.search.results.Contexts;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping on the text actually matched. Requires
 * HitConcordances as input (so we have the hit text available).
 */
public class HitPropertyHitText extends HitPropertyContextBase {

    public static HitPropertyHitText deserialize(Hits hits, String info) {
        return deserialize(HitPropertyHitText.class, hits, info);
    }

    public HitPropertyHitText(Hits hits, Annotation annotation, MatchSensitivity sensitivity, ContextSize contextSize) {
        super("hit text", "hit", hits, annotation, sensitivity, contextSize);
    }

    public HitPropertyHitText(Hits hits, Annotation annotation, MatchSensitivity sensitivity) {
        this(hits, annotation, sensitivity, null);
    }

    public HitPropertyHitText(Hits hits, Annotation annotation) {
        this(hits, annotation, null, null);
    }

    public HitPropertyHitText(Hits hits, MatchSensitivity sensitivity) {
        this(hits, null, sensitivity, null);
    }

    public HitPropertyHitText(Hits hits) {
        this(hits, null, null, null);
    }

    public HitPropertyHitText(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, ContextSize contextSize) {
        super("hit text", "hit", index, annotation, sensitivity, contextSize);
    }

    public HitPropertyHitText(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        this(index, annotation, sensitivity, null);
    }

    public HitPropertyHitText(BlackLabIndex index, MatchSensitivity sensitivity) {
        this(index, null, sensitivity, null);
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts) {
        return new HitPropertyHitText(newHits, annotation, sensitivity, contextSize).setContexts(contexts);
    }

    @Override
    public HitPropValueContextWords get(int hitNumber) {
        int[] context = contexts.get(hitNumber);
        int contextHitStart = context[Contexts.HIT_START_INDEX];
        int contextRightStart = context[Contexts.RIGHT_START_INDEX];
        int contextLength = context[Contexts.LENGTH_INDEX];

        // Copy the desired part of the context
        int n = contextRightStart - contextHitStart;
        if (n <= 0)
            return new HitPropValueContextWords(hits, annotation, new int[0], sensitivity);
        int[] dest = new int[n];
        int contextStart = contextLength * contextIndices.get(0) + Contexts.NUMBER_OF_BOOKKEEPING_INTS;
        System.arraycopy(context, contextStart + contextHitStart, dest, 0, n);
        return new HitPropValueContextWords(hits, annotation, dest, sensitivity);
    }

    @Override
    public int compare(Object i, Object j) {
        int[] ca = contexts.get((Integer) i);
        int caHitStart = ca[Contexts.HIT_START_INDEX];
        int caRightStart = ca[Contexts.RIGHT_START_INDEX];
        int caLength = ca[Contexts.LENGTH_INDEX];
        int[] cb = contexts.get((Integer) j);
        int cbHitStart = cb[Contexts.HIT_START_INDEX];
        int cbRightStart = cb[Contexts.RIGHT_START_INDEX];
        int cbLength = cb[Contexts.LENGTH_INDEX];

        // Compare the hit context for these two hits
        int contextIndex = contextIndices.get(0);
        int ai = caHitStart;
        int bi = cbHitStart;
        while (ai < caRightStart && bi < cbRightStart) {
            int cmp = terms.compareSortPosition(
                    ca[contextIndex * caLength + ai + Contexts.NUMBER_OF_BOOKKEEPING_INTS],
                    cb[contextIndex * cbLength + bi + Contexts.NUMBER_OF_BOOKKEEPING_INTS], sensitivity);
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
    
}
