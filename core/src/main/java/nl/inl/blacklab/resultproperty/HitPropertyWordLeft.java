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
 * A hit property for grouping on the context of the hit. Requires
 * HitConcordances as input (so we have the hit text available).
 */
public class HitPropertyWordLeft extends HitPropertyContextBase {

    public static HitPropertyWordLeft deserialize(Hits hits, String info) {
        return deserialize(HitPropertyWordLeft.class, hits, info);
    }

    public HitPropertyWordLeft(Hits hits, Annotation annotation, MatchSensitivity sensitivity, ContextSize contextSize) {
        super("word left", "wordleft", hits, annotation, sensitivity, contextSize);
    }

    public HitPropertyWordLeft(Hits hits, Annotation annotation, MatchSensitivity sensitivity) {
        this(hits, annotation, sensitivity, null);
    }

    public HitPropertyWordLeft(Hits hits, Annotation annotation) {
        this(hits, annotation, null, null);
    }

    public HitPropertyWordLeft(Hits hits, MatchSensitivity sensitivity) {
        this(hits, null, sensitivity, null);
    }

    public HitPropertyWordLeft(Hits hits) {
        this(hits, null, null, null);
    }

    public HitPropertyWordLeft(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, ContextSize contextSize) {
        super("word left", "wordleft", index, annotation, sensitivity, contextSize);
    }

    public HitPropertyWordLeft(BlackLabIndex index, MatchSensitivity sensitivity) {
        this(index, null, sensitivity, null);
    }

    @Override
    public HitPropertyWordLeft copyWithHits(Hits newHits) {
        return new HitPropertyWordLeft(newHits, annotation, sensitivity, contextSize);
    }

    @Override
    public HitPropValueContextWord get(int hitNumber) {
        int[] context = contexts.get(hitNumber);
        int contextHitStart = context[Contexts.HIT_START_INDEX];
        //int contextRightStart = context[Contexts.CONTEXTS_RIGHT_START_INDEX];
        int contextLength = context[Contexts.LENGTH_INDEX];

        if (contextHitStart <= 0)
            return new HitPropValueContextWord(hits, annotation, -1, sensitivity);
        int contextStart = contextLength * contextIndices.get(0) + Contexts.NUMBER_OF_BOOKKEEPING_INTS;
        return new HitPropValueContextWord(hits, annotation, context[contextStart
                + contextHitStart - 1], sensitivity);
    }

    @Override
    public int compare(Object i, Object j) {
        int[] ca = contexts.get((Integer) i);
        int caHitStart = ca[Contexts.HIT_START_INDEX];
        int caLength = ca[Contexts.LENGTH_INDEX];
        int[] cb = contexts.get((Integer) j);
        int cbHitStart = cb[Contexts.HIT_START_INDEX];
        int cbLength = cb[Contexts.LENGTH_INDEX];

        if (caHitStart <= 0)
            return cbHitStart <= 0 ? 0 : (reverse ? 1 : -1);
        if (cbHitStart <= 0)
            return reverse ? -1 : 1;
        // Compare one word to the left of the hit
        int contextIndex = contextIndices.get(0);

        int cmp = terms.compareSortPosition(
                ca[contextIndex * caLength + caHitStart - 1 + Contexts.NUMBER_OF_BOOKKEEPING_INTS],
                cb[contextIndex * cbLength + cbHitStart - 1 + Contexts.NUMBER_OF_BOOKKEEPING_INTS],
                sensitivity);
        return reverse ? -cmp : cmp;
    }

}
