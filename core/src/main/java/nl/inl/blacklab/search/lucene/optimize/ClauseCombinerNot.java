package nl.inl.blacklab.search.lucene.optimize;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryExpansion;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;

/**
 * Recognize "anytoken" clauses and combine them with their neighbour to create an expansion.
 * 
 * Can also combine two anytoken clauses into a new anytoken clause.
 */
class ClauseCombinerNot extends ClauseCombiner {
	
	private static final int PRIORITY = 4;

	enum Type {
		NOT_CONST,
		CONST_NOT,
	}
	
	Type getType(BLSpanQuery left, BLSpanQuery right) {
		if (left.isSingleTokenNot() && right.hitsAllSameLength())
			return Type.NOT_CONST;
		if (right.isSingleTokenNot() && left.hitsAllSameLength())
			return Type.CONST_NOT;
		return null;
	}
	
	@Override
	public int priority(BLSpanQuery left, BLSpanQuery right) {
		return getType(left, right) == null ? CANNOT_COMBINE : PRIORITY;
	}

	@Override
	public BLSpanQuery combine(BLSpanQuery left, BLSpanQuery right) {
		SpanQueryPositionFilter posf;
		BLSpanQuery container;
		switch(getType(left, right)) {
		case NOT_CONST:
			// Constant-length child after negative, single-token part.
			// Rewrite to NOTCONTAINING clause, incorporating previous part.
			int myLen = right.hitsLengthMin();
			container = new SpanQueryExpansion(right, true, 1, 1);
			posf = new SpanQueryPositionFilter(container, left.inverted(), SpanQueryPositionFilter.Operation.CONTAINING, true);
			posf.adjustRight(-myLen);
			return posf;
		case CONST_NOT:
			// Negative, single-token child after constant-length part.
			// Rewrite to NOTCONTAINING clause, incorporating previous part.
			int prevLen = left.hitsLengthMin();
			container = new SpanQueryExpansion(left, false, 1, 1);
			posf = new SpanQueryPositionFilter(container, right.inverted(), SpanQueryPositionFilter.Operation.CONTAINING, true);
			posf.adjustLeft(prevLen);
			return posf;
		}
		throw new UnsupportedOperationException("Cannot combine " + left + " and " + right);
	}
}
