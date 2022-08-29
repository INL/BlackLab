package nl.inl.blacklab.analysis;

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;

/**
 * For every token that is not the first value at this position
 * (e.g. tokenIncrement greater than zero, except for the first token
 * which is by definition the first value), add a small payload that
 * indicates this.
 *
 * The actual payload logic is this:
 * - if the first byte is 127, the next byte indicates whether this is a primary value (1) or not (0).
 * - if the first byte is NOT 127, this is a primary value and no bytes have been prepended here.
 *
 * This ensures we shouldn't have to change or add many payloads, as most values wiill be primary.
 *
 * This ensures that we can identify which value to
 * store in the forward index.
 */
public class AddIsPrimaryValueToPayloadFilter extends TokenFilter {

    private final PositionIncrementAttribute posIncAtt;

    private final PayloadAttribute payloadAtt;

    private boolean first;

    /**
     * @param input the token stream to desensitize
     */
    public AddIsPrimaryValueToPayloadFilter(TokenStream input) {
        super(input);
        posIncAtt = addAttribute(PositionIncrementAttribute.class);
        payloadAtt = addAttribute(PayloadAttribute.class);
        first = true;
    }

    @Override
    final public boolean incrementToken() throws IOException {
        if (input.incrementToken()) {
            // Is this a primary value (the first at this position) or a secondary one (any subsequent value
            // at the same position)?
            boolean isPrimary = first || posIncAtt.getPositionIncrement() > 0;
            payloadAtt.setPayload(PayloadUtils.addIsPrimary(isPrimary, payloadAtt.getPayload()));
            return true;
        }
        return false;
    }

    @Override public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof AddIsPrimaryValueToPayloadFilter))
            return false;
        if (!super.equals(o))
            return false;
        AddIsPrimaryValueToPayloadFilter that = (AddIsPrimaryValueToPayloadFilter) o;
        return first == that.first && posIncAtt.equals(that.posIncAtt) && payloadAtt.equals(that.payloadAtt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), posIncAtt, payloadAtt, first);
    }
}
