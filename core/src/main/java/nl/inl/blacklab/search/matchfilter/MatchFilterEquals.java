package nl.inl.blacklab.search.matchfilter;

import nl.inl.blacklab.search.Span;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexDocument;
import nl.inl.blacklab.search.lucene.HitQueryContext;

public class MatchFilterEquals extends MatchFilter {

	MatchFilter a, b;

	public MatchFilterEquals(MatchFilter a, MatchFilter b) {
		super();
		this.a = a;
		this.b = b;
	}

	@Override
	public String toString() {
		return a + " = " + b;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((a == null) ? 0 : a.hashCode());
		result = prime * result + ((b == null) ? 0 : b.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MatchFilterEquals other = (MatchFilterEquals) obj;
		if (a == null) {
			if (other.a != null)
				return false;
		} else if (!a.equals(other.a))
			return false;
		if (b == null) {
			if (other.b != null)
				return false;
		} else if (!b.equals(other.b))
			return false;
		return true;
	}

	@Override
	public void setHitQueryContext(HitQueryContext context) {
		a.setHitQueryContext(context);
		b.setHitQueryContext(context);
	}

	@Override
	public ConstraintValue evaluate(ForwardIndexDocument fiDoc, Span[] capturedGroups) {
		ConstraintValue ra = a.evaluate(fiDoc, capturedGroups);
		ConstraintValue rb = b.evaluate(fiDoc, capturedGroups);
		return ConstraintValue.get(ra.equals(rb));
	}

	@Override
	public void lookupPropertyNumbers(ForwardIndexAccessor fiAccessor) {
		a.lookupPropertyNumbers(fiAccessor);
		b.lookupPropertyNumbers(fiAccessor);
	}

}
