package nl.inl.blacklab.search.sequences;

import nl.inl.blacklab.search.QueryExecutionContext;
import nl.inl.blacklab.search.TextPattern;
import nl.inl.blacklab.search.TextPatternTranslator;

public class TextPatternExpansion extends TextPattern {

	protected TextPattern clause;

	protected boolean expandToLeft;

	/*
	 * The minimum number of tokens in this stretch.
	 */
	protected int min;

	/*
	 * The maximum number of tokens in this stretch.
	 */
	protected int max;

	public TextPatternExpansion(TextPattern clause, boolean expandToLeft, int min, int max) {
		this.clause = clause;
		this.expandToLeft = expandToLeft;
		this.min = min;
		this.max = max;
	}

	@Override
	public <T> T translate(TextPatternTranslator<T> translator, QueryExecutionContext context) {
		return translator.expand(context, clause.translate(translator, context), expandToLeft, min, max);
	}

	@Override
	public String toString() {
		return "EXPAND(" + clause + ", " + expandToLeft + ", " + min + "," + max + "]";
	}

	@Override
	public boolean matchesEmptySequence() {
		return clause.matchesEmptySequence() && min == 0;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof TextPatternExpansion) {
			TextPatternExpansion tp = ((TextPatternExpansion) obj);
			return clause.equals(tp.clause) && expandToLeft == tp.expandToLeft && min == tp.min && max == tp.max;
		}
		return false;
	}

	@Override
	public boolean hasConstantLength() {
		return clause.hasConstantLength() && min == max;
	}

	@Override
	public int getMinLength() {
		return clause.getMinLength() + min;
	}

	@Override
	public int getMaxLength() {
		return max < 0 ? -1 : clause.getMaxLength() + max;
	}

	public int getMinExpand() {
		return min;
	}

	public int getMaxExpand() {
		return max;
	}

	@Override
	public TextPattern combineWithPrecedingPart(TextPattern previousPart) {
		if (expandToLeft && previousPart instanceof TextPatternAnyToken) {
			// Expand to left following any token clause. Combine.
			TextPatternAnyToken tp = (TextPatternAnyToken)previousPart;
			return new TextPatternExpansion(clause, expandToLeft, min + tp.min, (max == -1 || tp.max == -1) ? -1 : max + tp.max);
		}
		if (!expandToLeft && max != min) {
			// Expand to right with range of tokens. Combine with previous part to likely
			// reduce the number of hits we'll have to expand.
			TextPattern seq = new TextPatternSequence(previousPart, clause);
			seq = seq.rewrite();
			return new TextPatternExpansion(seq, false, min, max);
		}
		return super.combineWithPrecedingPart(previousPart);
	}

	public boolean isExpandToLeft() {
		return expandToLeft;
	}

	public TextPattern getClause() {
		return clause;
	}

	@Override
	public TextPattern rewrite() {
		TextPattern rewritten = clause.rewrite();
		if (rewritten != clause) {
			return new TextPatternExpansion(rewritten, expandToLeft, min, max);
		}
		return this;
	}

	@Override
	public int hashCode() {
		return clause.hashCode() + 1023 * (expandToLeft ? 1 : 0) + 13 * min + 31 * max;
	}

}
