package nl.inl.blacklab.search;

/**
 * AND operation.
 * @deprecated use TextPatternAndNot
 */
@Deprecated
public class TextPatternAnd extends TextPatternAndNot {

	public TextPatternAnd(TextPattern... clauses) {
		super(clauses);
	}
}
