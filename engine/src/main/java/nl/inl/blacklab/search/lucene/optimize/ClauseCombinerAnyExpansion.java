package nl.inl.blacklab.search.lucene.optimize;

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.lucene.SpanQueryExpansion;
import nl.inl.blacklab.search.lucene.SpanQueryExpansion.Direction;

/**
 * Recognize "anytoken" clauses and combine them with their neighbour to create
 * an expansion.
 * 
 * Can also combine two anytoken clauses into a new anytoken clause.
 */
class ClauseCombinerAnyExpansion extends ClauseCombiner {

    private static final int BOTH_ANY_PRIORITY = 8; // prefer this over repetition, e.g. []* []* --> []*, not ([]*){2}

    private static final int EXPANSION_PRIORITY = 30;

    enum Type {
        LEFT_ANY,
        RIGHT_ANY,
        BOTH_ANY
    }

    Type getType(BLSpanQuery left, BLSpanQuery right) {
        boolean leftAny = left instanceof SpanQueryAnyToken;
        boolean rightAny = right instanceof SpanQueryAnyToken;
        if (!leftAny && !rightAny)
            return null;
        return leftAny && rightAny ? Type.BOTH_ANY : (leftAny ? Type.LEFT_ANY : Type.RIGHT_ANY);
    }

    @Override
    public int priority(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
        Type type = getType(left, right);
        if (type == null)
            return CANNOT_COMBINE;
        if (type == Type.BOTH_ANY)
            return BOTH_ANY_PRIORITY;
        return EXPANSION_PRIORITY;
    }

    @Override
    public BLSpanQuery combine(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
        SpanQueryAnyToken any, any2;
        switch (getType(left, right)) {
        case LEFT_ANY:
            // Expand to left
            any = (SpanQueryAnyToken) left;
            return new SpanQueryExpansion(right, Direction.LEFT,
                    any.guarantees().hitsLengthMin(), any.guarantees().hitsLengthMax());
        case RIGHT_ANY:
            // Expand to right
            any = (SpanQueryAnyToken) right;
            return new SpanQueryExpansion(left, Direction.RIGHT,
                    any.guarantees().hitsLengthMin(), any.guarantees().hitsLengthMax());
        case BOTH_ANY:
            // Combine two anytoken clauses
            any = (SpanQueryAnyToken) left;
            any2 = (SpanQueryAnyToken) right;
            return any.addRep(any2.guarantees().hitsLengthMin(), any2.guarantees().hitsLengthMax());
        }
        throw new UnsupportedOperationException("Cannot combine " + left + " and " + right);
    }

    @Override
    public String toString() {
        return "CCAnyExpansion";
    }
}
