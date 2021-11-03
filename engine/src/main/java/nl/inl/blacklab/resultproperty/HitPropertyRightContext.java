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
import nl.inl.blacklab.search.results.Hits;

/**
 * A hit property for grouping on the context of the hit. Requires
 * HitConcordances as input (so we have the hit text available).
 */
public class HitPropertyRightContext extends HitPropertyContextBase {

    protected final ContextSize contextSize;
    
    static HitPropertyRightContext deserializeProp(BlackLabIndex index, AnnotatedField field, String info) {
        return deserializeProp(HitPropertyRightContext.class, index, field, info);
    }

    HitPropertyRightContext(HitPropertyRightContext prop, Hits hits, Contexts contexts, boolean invert) {
        super(prop, hits, contexts, invert);
        this.contextSize = prop.contextSize;
    }

    public HitPropertyRightContext(HitPropertyLeftContext prop, Hits hits, Contexts contexts, boolean invert) {
        super(prop, hits, contexts, invert);
        this.contextSize = prop.contextSize;
    }

    public HitPropertyRightContext(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity, ContextSize contextSize) {
        super("right context", "right", index, annotation, sensitivity);
        this.contextSize = contextSize != null ? contextSize : index.defaultContextSize();
    }

    public HitPropertyRightContext(BlackLabIndex index, Annotation annotation, MatchSensitivity sensitivity) {
        this(index, annotation, sensitivity, null);
    }

    public HitPropertyRightContext(BlackLabIndex index, MatchSensitivity sensitivity) {
        this(index, null, sensitivity, null);
    }

    public HitPropertyRightContext(BlackLabIndex index, Annotation annotation) {
        this(index, annotation, null, null);
    }
    
    @Override
    public HitProperty copyWith(Hits newHits, Contexts contexts, boolean invert) {
        return new HitPropertyRightContext(this, newHits, contexts, invert);
    }

    @Override
    public PropertyValueContextWords get(int hitIndex) {
        int[] context = contexts.get(hitIndex);
        //int contextHitStart = context[Contexts.CONTEXTS_HIT_START_INDEX];
        int contextRightStart = context[Contexts.RIGHT_START_INDEX];
        int contextLength = context[Contexts.LENGTH_INDEX];

        // Copy the desired part of the context
        int n = contextLength - contextRightStart;
        if (n <= 0)
            return new PropertyValueContextWords(index, annotation, sensitivity, new int[0], false);
        int[] dest = new int[n];
        int contextStart = contextLength * contextIndices.getInt(0) + Contexts.NUMBER_OF_BOOKKEEPING_INTS;
        System.arraycopy(context, contextStart + contextRightStart, dest, 0, n);
        return new PropertyValueContextWords(index, annotation, sensitivity, dest, false);
    }

    @Override
    public int compare(int indexA, int indexB) {
        int[] ca = contexts.get(indexA);
        int caRightStart = ca[Contexts.RIGHT_START_INDEX];
        int caLength = ca[Contexts.LENGTH_INDEX];
        int[] cb = contexts.get(indexB);
        int cbRightStart = cb[Contexts.RIGHT_START_INDEX];
        int cbLength = cb[Contexts.LENGTH_INDEX];

        // Compare the right context for these two hits
        int contextIndex = contextIndices.getInt(0);
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
