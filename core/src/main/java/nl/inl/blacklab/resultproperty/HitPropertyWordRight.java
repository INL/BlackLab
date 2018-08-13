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
import nl.inl.blacklab.search.results.Hit;
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping on the context of the hit. Requires
 * HitConcordances as input (so we have the hit text available).
 */
public class HitPropertyWordRight extends HitPropertyContextBase {

    public static HitPropertyWordRight deserialize(Hits hits, String info) {
        return deserialize(HitPropertyWordRight.class, hits, info);
    }

    HitPropertyWordRight(HitPropertyWordRight prop, Hits hits, Contexts contexts, boolean invert) {
        super(prop, hits, contexts, invert);
    }

    public HitPropertyWordRight(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, ContextSize contextSize) {
        super("word right", "wordright", index, annotation, sensitivity, contextSize);
    }

    public HitPropertyWordRight(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        this(index, annotation, sensitivity, null);
    }

    public HitPropertyWordRight(BlackLabIndex index, Annotation annotation) {
        this(index, annotation, null, null);
    }

    public HitPropertyWordRight(BlackLabIndex index) {
        this(index, null, null, null);
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        return new HitPropertyWordRight(this, newHits, contexts, invert);
    }

    @Override
    public HitPropValueContextWord get(Hit result) {
        int[] context = contexts.get(result);
        int contextRightStart = context[Contexts.RIGHT_START_INDEX];
        int contextLength = context[Contexts.LENGTH_INDEX];

        if (contextLength <= contextRightStart)
            return new HitPropValueContextWord(hits, annotation, -1, sensitivity);
        int contextStart = contextLength * contextIndices.get(0) + Contexts.NUMBER_OF_BOOKKEEPING_INTS;
        return new HitPropValueContextWord(hits, annotation, context[contextStart + contextRightStart], sensitivity);
    }

    @Override
    public int compare(Hit a, Hit b) {
        int[] ca = contexts.get(a);
        int caRightStart = ca[Contexts.RIGHT_START_INDEX];
        int caLength = ca[Contexts.LENGTH_INDEX];
        int[] cb = contexts.get(b);
        int cbRightStart = cb[Contexts.RIGHT_START_INDEX];
        int cbLength = cb[Contexts.LENGTH_INDEX];

        if (caLength <= caRightStart)
            return cbLength <= cbRightStart ? 0 : (reverse ? 1 : -1);
        if (cbLength <= cbRightStart)
            return reverse ? -1 : 1;
        // Compare one word to the right of the hit
        int contextIndex = contextIndices.get(0);
        int cmp = terms.compareSortPosition(
                ca[contextIndex * caLength + caRightStart + Contexts.NUMBER_OF_BOOKKEEPING_INTS],
                cb[contextIndex * cbLength + cbRightStart + Contexts.NUMBER_OF_BOOKKEEPING_INTS],
                sensitivity);
        return reverse ? -cmp : cmp;
    }

}
