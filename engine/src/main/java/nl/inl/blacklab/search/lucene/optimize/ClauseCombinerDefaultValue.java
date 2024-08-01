package nl.inl.blacklab.search.lucene.optimize;

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryDefaultValue;

/**
 * Recognize "default value" clause (which just means []* unless it's used as a function argument,
 * in which case it's replaced with the default value of that argument).
 */
class ClauseCombinerDefaultValue extends ClauseCombiner {

    private static final int PRIORITY = 8; // prefer this over repetition, e.g. _ _ --> _, not _{2}

    @Override
    public int priority(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
        return left instanceof SpanQueryDefaultValue && right instanceof SpanQueryDefaultValue ?
                PRIORITY :
                CANNOT_COMBINE;
    }

    @Override
    public BLSpanQuery combine(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
        if (canCombine(left, right, reader)) {
            return left; // reduce to single default value (e.g. []* []* -> []*)
        }
        throw new UnsupportedOperationException("Cannot combine " + left + " and " + right);
    }

    @Override
    public String toString() {
        return "CCDefaultValue";
    }
}
