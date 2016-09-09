package nl.inl.blacklab.search;

import nl.inl.blacklab.search.TextPatternPositionFilter.Operation;

public class TextPatternFilterNGrams extends TextPattern {

	protected TextPattern clause;

	protected Operation op;

	/*
	 * The minimum hit length
	 */
	protected int min;

	/*
	 * The maximum hit length
	 */
	protected int max;

	public TextPatternFilterNGrams(TextPattern clause, Operation op, int min, int max) {
		this.clause = clause;
		this.op = op;
		this.min = min;
		this.max = max;
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		return translator.filterNGrams(context, clause.translate(translator, context), op, min, max);
	}

	@Override
	public boolean matchesEmptySequence() {
		return clause.matchesEmptySequence() && min == 0;
	}

	@Override
	public TextPattern noEmpty() {
		if (!matchesEmptySequence())
			return this;
		int newMin = min == 0 ? 1 : min;
		return new TextPatternFilterNGrams(clause.noEmpty(), op, newMin, max);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternFilterNGrams) {
			TextPatternFilterNGrams tp = ((TextPatternFilterNGrams) obj);
			return clause.equals(tp.clause) && op == tp.op && min == tp.min && max == tp.max;
		}
		return false;
	}

	@Override
	public boolean hasConstantLength() {
		return clause.hasConstantLength() && min == max;
	}

	@Override
	public int getMinLength() {
		return min;
	}

	@Override
	public int getMaxLength() {
		return max;
	}

	@Override
	public TextPattern combineWithPrecedingPart(TextPattern previousPart) {
		if ((op == Operation.CONTAINING_AT_END || op == Operation.ENDS_AT) && previousPart instanceof TextPatternAnyToken) {
			// Expand to left following any token clause. Combine.
			TextPatternAnyToken tp = (TextPatternAnyToken)previousPart;
			return new TextPatternFilterNGrams(clause, op, min + tp.min, (max == -1 || tp.max == -1) ? -1 : max + tp.max);
		}
		if ((op == Operation.CONTAINING_AT_START || op == Operation.STARTS_AT) && max != min) {
			// Expand to right with range of tokens. Combine with previous part to likely
			// reduce the number of hits we'll have to expand.
			TextPattern seq = new TextPatternSequence(previousPart, clause);
			seq = seq.rewrite();
			return new TextPatternFilterNGrams(seq, op, min, max);
		}
		return super.combineWithPrecedingPart(previousPart);
	}

	public Operation getOperation() {
		return op;
	}

	public TextPattern getClause() {
		return clause;
	}

	@Override
	public TextPattern rewrite() {
		TextPattern rewritten = clause.rewrite();
		if (rewritten != clause) {
			return new TextPatternFilterNGrams(rewritten, op, min, max);
		}
		return this;
	}

	@Override
	public int hashCode() {
		return clause.hashCode() + op.hashCode() + 13 * min + 31 * max;
	}

	@Deprecated
	@Override
	public String toString(QueryExecutionContext context) {
		return "FILTERNGRAMS(" + clause.toString(context) + ", " + op + ", " + min + ", " + max + ")";
	}

	@Override
	public String toString() {
		return "FILTERNGRAMS(" + clause + ", " + op + ", " + min + ", " + max + ")";
	}

}
