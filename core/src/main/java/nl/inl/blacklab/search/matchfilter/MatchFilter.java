package nl.inl.blacklab.search.matchfilter;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.lucene.HitQueryContext;

public abstract class MatchFilter {

	@Override
	public abstract String toString();

	@Override
	public abstract int hashCode();

	@Override
	public abstract boolean equals(Object obj);

	/**
	 * Pass the hit query context object to this constraint,
	 * so we can look up the group numbers we need.
	 * @param context hit query context object
	 */
	public abstract void setHitQueryContext(HitQueryContext context);

	/**
	 * Evaluate the constraint at the current match position.
	 * @param fiDoc document we're matching in right now
	 * @param capturedGroups current captured groups state
	 * @return value of the constraint at this position
	 */
	public abstract ConstraintValue evaluate(ForwardIndexDocument fiDoc, Span[] capturedGroups);

	/**
	 * Make sur
	 * @param fiAccessor
	 */
	public abstract void lookupPropertyNumbers(ForwardIndexAccessor fiAccessor);

}
