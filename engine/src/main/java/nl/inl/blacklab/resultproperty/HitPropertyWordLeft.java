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

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
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
    
    protected static final ContextSize contextSize = ContextSize.get(1, 0, false);
    
    static HitPropertyWordLeft deserializeProp(BlackLabIndex index, AnnotatedField field, String info) {
        return deserializeProp(HitPropertyWordLeft.class, index, field, info);
    }

    HitPropertyWordLeft(HitPropertyWordLeft prop, Hits hits, Contexts contexts, boolean invert) {
        super(prop, hits, contexts, invert);
    }

    public HitPropertyWordLeft(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, ContextSize contextSize) {
        super("word left", "wordleft", index, annotation, sensitivity);
    }

    public HitPropertyWordLeft(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        this(index, annotation, sensitivity, null);
    }

    public HitPropertyWordLeft(BlackLabIndex index, Annotation annotation) {
        this(index, annotation, null, null);
    }

    public HitPropertyWordLeft(BlackLabIndex index) {
        this(index, null, null);
    }

    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        return new HitPropertyWordLeft(this, newHits, contexts, invert);
    }

    @Override
    public PropertyValueContextWord get(int hitIndex) {
        int[] context = contexts.get(hitIndex);
        int contextHitStart = context[Contexts.HIT_START_INDEX];
        //int contextRightStart = context[Contexts.CONTEXTS_RIGHT_START_INDEX];
        int contextLength = context[Contexts.LENGTH_INDEX];

        if (contextHitStart <= 0)
            return new PropertyValueContextWord(index, annotation, sensitivity, Terms.NO_TERM);
        int contextStart = contextLength * contextIndices.getInt(0) + Contexts.NUMBER_OF_BOOKKEEPING_INTS;
        return new PropertyValueContextWord(index, annotation, sensitivity, context[contextStart
                        + contextHitStart - 1]);
    }

    @Override
    public int compare(int indexA, int indexB) {
        int[] ca = contexts.get(indexA);
        int caHitStart = ca[Contexts.HIT_START_INDEX];
        int caLength = ca[Contexts.LENGTH_INDEX];
        int[] cb = contexts.get(indexB);
        int cbHitStart = cb[Contexts.HIT_START_INDEX];
        int cbLength = cb[Contexts.LENGTH_INDEX];

        if (caHitStart <= 0)
            return cbHitStart <= 0 ? 0 : (reverse ? 1 : -1);
        if (cbHitStart <= 0)
            return reverse ? -1 : 1;
        // Compare one word to the left of the hit
        int contextIndex = contextIndices.getInt(0);

        int cmp = terms.compareSortPosition(
                ca[contextIndex * caLength + caHitStart - 1 + Contexts.NUMBER_OF_BOOKKEEPING_INTS],
                cb[contextIndex * cbLength + cbHitStart - 1 + Contexts.NUMBER_OF_BOOKKEEPING_INTS],
                sensitivity);
        return reverse ? -cmp : cmp;
    }

    @Override
    public boolean isDocPropOrHitText() {
        return false;
    }
    
    @Override
    public int hashCode() {
        return 31 * super.hashCode() + contextSize.hashCode();
    }

    @Override
    public ContextSize needsContextSize(BlackLabIndex index) {
        return contextSize;
    }
}
