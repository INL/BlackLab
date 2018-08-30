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

    private static final int PRIORITY = 3;

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
        return getType(left, right) == null ? CANNOT_COMBINE : PRIORITY;
    }

    @Override
    public BLSpanQuery combine(BLSpanQuery left, BLSpanQuery right, IndexReader reader) {
        SpanQueryAnyToken any, any2;
        SpanQueryExpansion result;
        switch (getType(left, right)) {
        case LEFT_ANY:
            // Expand to left
            any = (SpanQueryAnyToken) left;
            result = new SpanQueryExpansion(right, Direction.LEFT, any.hitsLengthMin(), any.hitsLengthMax());
            return result;
        case RIGHT_ANY:
            // Expand to right
            any = (SpanQueryAnyToken) right;
            result = new SpanQueryExpansion(left, Direction.RIGHT, any.hitsLengthMin(), any.hitsLengthMax());
            return result;
        case BOTH_ANY:
            // Combine two anytoken clauses
            any = (SpanQueryAnyToken) left;
            any2 = (SpanQueryAnyToken) right;
            return any.addRep(any2.hitsLengthMin(), any2.hitsLengthMax());
        }
        throw new UnsupportedOperationException("Cannot combine " + left + " and " + right);
    }

    @Override
    public String toString() {
        return "CCAnyExpansion";
    }
}
