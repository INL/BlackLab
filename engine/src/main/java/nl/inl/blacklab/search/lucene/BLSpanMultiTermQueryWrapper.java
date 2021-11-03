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

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.Nfa;
import nl.inl.blacklab.search.fimatch.NfaState;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.util.StringUtil;

/**
 * Subclasses SpanMultiTermQueryWrapper so it correctly produces BLSpanOrQuery
 * or BLSpanTermQuery.
 *
 * @param <Q> the type of query we're wrapping
 */
public class BLSpanMultiTermQueryWrapper<Q extends MultiTermQuery>
        extends BLSpanQuery {

    SpanMultiTermQueryWrapper<Q> query;

    Term term;

    public BLSpanMultiTermQueryWrapper(QueryInfo queryInfo, Q query) {
        super(queryInfo);
        try {
            // Use reflection to get at inaccesible field MultiTermQuery.field.
            // We need this in order to (decide whether to) optimize this to an NFA.
            Field fldTerm = AutomatonQuery.class.getDeclaredField("term");
            fldTerm.setAccessible(true);
            this.term = (Term) fldTerm.get(query);
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException e) {
            throw BlackLabRuntimeException.wrap(e);
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
        BLSpanQuery result = BLSpanQuery.wrap(queryInfo, (SpanQuery) q);
        if (result.getField() == null) {
            if (result instanceof BLSpanOrQuery) {
                BLSpanOrQuery or = (BLSpanOrQuery) result;
                or.setHitsAreFixedLength(1);
                or.setClausesAreSimpleTermsInSameProperty(true);
                or.setField(getRealField());
            } else {
                throw new BlackLabRuntimeException("BLSpanMultiTermQueryWrapper rewritten to " +
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
        final int prime = 31;
        int result = 1;
        result = prime * result + ((query == null) ? 0 : query.hashCode());
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
        BLSpanMultiTermQueryWrapper<?> other = (BLSpanMultiTermQueryWrapper<?>) obj;
        if (query == null) {
            if (other.query != null)
                return false;
        } else if (!query.equals(other.query))
            return false;
        return true;
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
            // NOTE: we'd like to use Pattern.quote() here instead, but not sure if Lucene's regex engine supports \Q and \E.
            regex = "^" + StringUtil.escapeRegexCharacters(pattern) + ".*$";
        } else {
            throw new UnsupportedOperationException("Cannot make regex from " + wrapped);
        }
        return regex;
    }

    @Override
    public boolean canMakeNfa() {
        // Subproperties aren't stored in forward index, so we can't match them using NFAs
        return !term.text().contains(AnnotatedFieldNameUtil.SUBANNOTATION_SEPARATOR);
    }

    @Override
    public long reverseMatchingCost(IndexReader reader) {
        String pattern = term.text();
        Query wrapped = query.getWrappedQuery();
        int numberOfChars;
        if (wrapped instanceof RegexpQuery) {
            numberOfChars = countRegexWordCharacters(pattern);
        } else if (wrapped instanceof WildcardQuery) {
            numberOfChars = pattern.replaceAll("[\\*\\?]", "").length();
        } else if (wrapped instanceof PrefixQuery) {
            numberOfChars = pattern.length();
        } else {
            // Don't know; just use reverse matching
            numberOfChars = 5;
        }
        long n;
        try {
            n = reader.getSumTotalTermFreq(term.field()); // total terms in field
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
        // Make a very rough estimate of the number of terms that could match
        // this. We tend to over-guess by quite a lot, because clauses matching lots
        // of terms benefit a lot from using NFAs (more than a single very frequent term),
        // and clauses that don't match that
        // many likely aren't slowed down a lot by using NFAs. Also, people tend to
        // ask common pre- and suffixes more often than rare ones.
        // All in all, it's really a wild guess, but it's all we have right now.
        switch (numberOfChars) {
        case 1:
            return n / 2; // e.g. d.*
        case 2:
            return n / 3; // e.g. de.*
        case 3:
            return n / 20; // e.g. der.*
        case 4:
            return n / 100; // e.g. dera.*
        default:
            // 5 or more characters given.
            // We have no idea how many hits we're likely to get from this.
            // Let's assume not too many, so we will likely use regular reverse matching.
            return n > 1_000_000 ? n / 1_000_000 : 1;
        }
    }

    /**
     * Count word characters in the regex.
     *
     * We use this to (gu)estimate how slow resolving the terms matching this regex will
     * likely be.
     *
     * @param pattern regex pattern
     * @return number of word characters in the pattern
     */
    public static int countRegexWordCharacters(String pattern) {
        String trimmed = pattern.replaceAll("^\\^(\\(\\?\\-?[ic]\\))?|\\$$", ""); // trim off ^, $ and (?-i), etc.
        // only retain word characters
        return trimmed.replaceAll("\\W", "").length();
        //trimmed.replaceAll("^(\\w*)(\\W(|.*\\W))(\\w*)$", "$1$4"); // only retain prefix and suffix
    }

    @Override
    public int forwardMatchingCost() {
        return BLSpanTermQuery.FIXED_FORWARD_MATCHING_COST * 3 / 2; // more expensive than a single term, because we have to do FI lookup and regex matching
    }

    @Override
    public String getRealField() {
        return query.getField();
    }

}
