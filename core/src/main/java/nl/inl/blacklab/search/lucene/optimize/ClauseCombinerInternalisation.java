package nl.inl.blacklab.search.lucene.optimize;

import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryAnyToken;
import nl.inl.blacklab.search.lucene.SpanQueryExpansion;
import nl.inl.blacklab.search.lucene.SpanQueryPositionFilter;

/**
 * Some types of clauses try to "gobble" up adjacent tokens in order
 * to improve optimization.
 */
class ClauseCombinerInternalisation extends ClauseCombiner {

	private static final int PRIORITY = 2;

	enum Type {
		EXPAND_ANY,
		ANY_EXPAND,
		EXPAND_CLAUSE,
		CLAUSE_EXPAND,
		POSFILTER_CONST,
		CONST_POSFILTER,
	}
	
	Type getType(BLSpanQuery left, BLSpanQuery right) {
		boolean leftExpand = left instanceof SpanQueryExpansion;
		boolean rightExpand = right instanceof SpanQueryExpansion;
		boolean leftExpandToLeft = leftExpand && ((SpanQueryExpansion)left).isExpandToLeft();
		boolean rightExpandToLeft = rightExpand && ((SpanQueryExpansion)right).isExpandToLeft();
		boolean leftAny = left instanceof SpanQueryAnyToken;
		boolean rightAny = right instanceof SpanQueryAnyToken;
		if (leftExpand && !leftExpandToLeft && rightAny)
			return Type.EXPAND_ANY;
		if (rightExpand && rightExpandToLeft && leftAny)
			return Type.ANY_EXPAND;
		if (leftExpand && leftExpandToLeft)
			return Type.EXPAND_CLAUSE;
		if (rightExpand && !rightExpandToLeft)
			return Type.CLAUSE_EXPAND;
		if (left instanceof SpanQueryPositionFilter && right.hitsAllSameLength())
			return Type.POSFILTER_CONST;
		if (right instanceof SpanQueryPositionFilter && left.hitsAllSameLength())
			return Type.CONST_POSFILTER;
		return null;
	}
	
	@Override
	public int priority(BLSpanQuery left, BLSpanQuery right) {
		return getType(left, right) == null ? CANNOT_COMBINE : PRIORITY;
	}

	@Override
	public BLSpanQuery combine(BLSpanQuery left, BLSpanQuery right) {
		SpanQueryExpansion exp;
		SpanQueryAnyToken any;
		SpanQueryPositionFilter posf;
		switch(getType(left, right)) {
		case EXPAND_ANY:
			// Any token clause after expand to right; combine.
			exp = (SpanQueryExpansion)left;
			any = (SpanQueryAnyToken)right;
			return exp.addExpand(any.hitsLengthMin(), any.hitsLengthMax());
		case ANY_EXPAND:
			// Any token clause before expand to left; combine.
			exp = (SpanQueryExpansion)right;
			any = (SpanQueryAnyToken)left;
			return exp.addExpand(any.hitsLengthMin(), any.hitsLengthMax());
		case EXPAND_CLAUSE:
			// Expansion to left followed by a clause. Internalise clause.
			exp = (SpanQueryExpansion)left;
			return exp.internalize(right);
		case CLAUSE_EXPAND:
			// Expansion to right preceded by a clause. Internalise clause.
			exp = (SpanQueryExpansion)right;
			return exp.internalize(left);
		case POSFILTER_CONST:
			// Position filter query followed by constant-length query. Internalize.
			posf = (SpanQueryPositionFilter)left;
			return posf.internalize(right, true);
		case CONST_POSFILTER:
			// Position filter query preceded by constant-length query. Internalize.
			posf = (SpanQueryPositionFilter)right;
			return posf.internalize(left, false);
		}
		throw new UnsupportedOperationException("Cannot combine " + left + " and " + right);
	}
}
