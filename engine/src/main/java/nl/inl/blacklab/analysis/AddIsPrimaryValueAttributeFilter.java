package nl.inl.blacklab.analysis;

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

/**
 * For every token that is not the first value at this position
 * (e.g. tokenIncrement greater than zero, except for the first token
 * which is by definition the first value), add a small payload that
 * indicates this.
 *
 * This ensures that we can identify which value to
 * store in the forward index.
 */
public class AddIsPrimaryValueAttributeFilter extends TokenFilter {

    private final PositionIncrementAttribute posIncAtt;

    private final IsPrimaryValueAttribute isPrimaryValueAtt;

    private boolean first;

    /**
     * @param input the token stream to desensitize
     */
    public AddIsPrimaryValueAttributeFilter(TokenStream input) {
        super(input);
        posIncAtt = addAttribute(PositionIncrementAttribute.class);
        isPrimaryValueAtt = addAttribute(IsPrimaryValueAttribute.class);
        first = true;
    }

    @Override
    final public boolean incrementToken() throws IOException {
        if (input.incrementToken()) {
            // Is this a primary value (the first at this position) or a secondary one (any subsequent value
            // at the same position)?
            isPrimaryValueAtt.setPrimaryValue(first || posIncAtt.getPositionIncrement() > 0);
            return true;
        }
        return false;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof AddIsPrimaryValueAttributeFilter))
            return false;
        if (!super.equals(o))
            return false;
        AddIsPrimaryValueAttributeFilter that = (AddIsPrimaryValueAttributeFilter) o;
        return first == that.first && posIncAtt.equals(that.posIncAtt) && isPrimaryValueAtt.equals(that.isPrimaryValueAtt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), posIncAtt, isPrimaryValueAtt, first);
    }
}
