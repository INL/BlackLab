package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.forwardindex.Terms;

public abstract class HitPropValueContext extends HitPropValue {

	protected Terms terms;

	public HitPropValueContext(Terms terms) {
		this.terms = terms;
	}
}
