package nl.inl.blacklab.search.grouping;

import nl.inl.blacklab.forwardindex.Terms;
import nl.inl.blacklab.search.Searcher;

public abstract class HitPropValueContext extends HitPropValue {

	protected Searcher searcher;

	protected String fieldPropName;

	protected Terms terms;

	public HitPropValueContext(Searcher searcher, String fieldPropName) {
		this.searcher = searcher;
		this.fieldPropName = fieldPropName;
		this.terms = searcher.getForwardIndex(fieldPropName).getTerms();
	}
}
