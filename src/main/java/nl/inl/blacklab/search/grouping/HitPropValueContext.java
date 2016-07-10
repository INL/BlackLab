package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.Hits;

public abstract class HitPropValueContext extends HitPropValue {

	protected String fieldName;

	protected Terms terms;

	protected String propName;

	public HitPropValueContext(Hits hits, String propName) {
		this.fieldName = hits.settings().concordanceField();
		this.propName = propName;
		this.terms = hits.getSearcher().getForwardIndex(ComplexFieldUtil.propertyField(fieldName, propName)).getTerms();
	}
}
