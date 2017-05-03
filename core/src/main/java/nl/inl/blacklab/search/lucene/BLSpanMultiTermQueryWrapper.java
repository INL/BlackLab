package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.search.spans.SpanMultiTermQueryWrapper;
import org.apache.lucene.search.spans.SpanQuery;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.Nfa;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.util.StringUtil;

/**
 * Subclasses SpanMultiTermQueryWrapper so it correctly produces
 * BLSpanOrQuery or BLSpanTermQuery.
 * @param <Q> the type of query we're wrapping
 */
public class BLSpanMultiTermQueryWrapper<Q extends MultiTermQuery>
		extends BLSpanQuery {

	SpanMultiTermQueryWrapper<Q> query;

	Term term;

	public BLSpanMultiTermQueryWrapper(Q query) {
		try {
			// Use reflection to get at inaccesible field MultiTermQuery.field.
			// We need this in order to (decide whether to) optimize this to an NFA.
			Field fldTerm = AutomatonQuery.class.getDeclaredField("term");
			fldTerm.setAccessible(true);
			this.term = (Term)fldTerm.get(query);
		} catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
			throw new RuntimeException(e);
		}
		this.query = new SpanMultiTermQueryWrapper<>(query);
	}

	@Override
	public String toString(String field) {
		return "SPANWRAP(" + query.getWrappedQuery() + ")";
	}

	@Override
	public BLSpanQuery rewrite(IndexReader reader) throws IOException {
		Query q = query.rewrite(reader);
		if (!(q instanceof SpanQuery))
			throw new UnsupportedOperationException(
					"You can only use BLSpanMultiTermQueryWrapper with a suitable SpanRewriteMethod.");
		BLSpanQuery result = BLSpanQuery.wrap((SpanQuery) q);
		if (result.getField() == null) {
			if (result instanceof BLSpanOrQuery) {
				((BLSpanOrQuery) result).setField(getField());
			} else {
				throw new RuntimeException("BLSpanMultiTermQueryWrapper rewritten to " +
						result.getClass().getSimpleName() + ", getField() == null");
			}
		}
		return result;
	}

	@Override
	public BLSpanWeight createWeight(IndexSearcher searcher, boolean needsScores)
			throws IOException {
		throw new IllegalArgumentException("Rewrite first!");
	}

	@Override
	public int hashCode() {
		return query.hashCode() ^ 0xB1ACC1AB;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj instanceof BLSpanMultiTermQueryWrapper) {
			BLSpanMultiTermQueryWrapper<?> other = (BLSpanMultiTermQueryWrapper<?>)obj;
			return query.equals(other.query);
		}
		return false;
	}

	@Override
	public boolean hitsAllSameLength() {
		return true;
	}

	@Override
	public int hitsLengthMin() {
		return 1;
	}

	@Override
	public int hitsLengthMax() {
		return 1;
	}

	@Override
	public boolean hitsEndPointSorted() {
		return true;
	}

	@Override
	public boolean hitsStartPointSorted() {
		return true;
	}

	@Override
	public boolean hitsHaveUniqueStart() {
		return true;
	}

	@Override
	public boolean hitsHaveUniqueEnd() {
		return true;
	}

	@Override
	public boolean hitsAreUnique() {
		return true;
	}

	@Override
	public Nfa getNfa(ForwardIndexAccessor fiAccessor, int direction) {
		NfaState state = NfaState.regex(getRealField(), getRegex(), null);
		return new Nfa(state, Arrays.asList(state));
	}

	protected String getRegex() {
		String pattern = term.text();
		Query wrapped = query.getWrappedQuery();
		String regex;
		if (wrapped instanceof RegexpQuery) {
			regex = pattern;
		} else if (wrapped instanceof WildcardQuery) {
			regex = StringUtil.wildcardToRegex(pattern);
		} else if (wrapped instanceof PrefixQuery) {
			regex = "^" + StringUtil.escapeRegexCharacters(pattern) + ".*$";
		} else {
			throw new UnsupportedOperationException("Cannot make regex from " + wrapped);
		}
		return regex;
	}

	@Override
	public boolean canMakeNfa() {
		// Subproperties aren't stored in forward index, so we can't match them using NFAs
		if (term.text().contains(ComplexFieldUtil.SUBPROPERTY_SEPARATOR))
			return false;

		return true;
	}

	@Override
	public long reverseMatchingCost(IndexReader reader) {
		String pattern = term.text();
		Query wrapped = query.getWrappedQuery();
		int numberOfChars;
		if (wrapped instanceof RegexpQuery) {
			String prefixPostfix = findRegexPrefixSuffix(pattern);
			numberOfChars = prefixPostfix.length();
		} else if (wrapped instanceof WildcardQuery) {
			numberOfChars = pattern.replaceAll("[\\*\\?]", "").length();
		} else if (wrapped instanceof PrefixQuery) {
			numberOfChars = pattern.length();
		} else {
			// Don't know; just use reverse matching
			numberOfChars = 5;
		}
		// We always specify 100 for NFA matching cost, so we can
		// use values 101 and 99 here to decide which approach to use for
		// this clause.
		// If 4 or less 'fixed' characters in pattern, use NFA;
		// otherwise use regular reverse matching.
		long n;
		try {
			n = reader.getSumTotalTermFreq(term.field()); // total terms in field
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		// Make a very rough estimate of the number of terms that could match
		// this. We tend to guess on the high side, because clauses matching lots
		// of terms benefit a lot from using NFAs, and clauses that don't match that
		// many likely aren't slowed down a lot by using NFAs.
		// All in all, it's really a wild guess, but it's all we have right now.
		switch (numberOfChars) {
		case 1:
			return n / 10;
		case 2:
			return n / 10 / 8;
		case 3:
			return n / 10 / 8 / 5;
		case 4:
			return n / 10 / 8 / 5 / 3;
		default:
			// 5 or more characters given.
			// We have no idea how many hits we're likely to get from this.
			// Let's assume not too many, so we will likely use regular reverse matching.
			return n > 1000000 ? n / 1000000 : 1;
		}
	}

	/**
	 * Strip everything out of the regex except a fixed prefix and suffix.
	 * We use this to (gu)estimate how slow resolving the terms matching this regex will likely be.
	 *
	 * @param pattern regex pattern
	 * @return only the prefix and suffix of the pattern
	 */
	public static String findRegexPrefixSuffix(String pattern) {
		String trimmed = pattern.replaceAll("^\\^(\\(\\?\\-?[ic]\\))?|\\$$", ""); // trim off ^, $ and (?-i), etc.
		String prefixPostfix = trimmed.replaceAll("^(\\w+)(\\W(|.*\\W))(\\w+)$", "$1$4"); // only retain prefix and suffix
		return prefixPostfix;
	}

	@Override
	public int forwardMatchingCost() {
		return 5; // more expensive than a single term, because we have to do FI lookup and regex matching
	}

	@Override
	public String getRealField() {
		return query.getField();
	}


}
