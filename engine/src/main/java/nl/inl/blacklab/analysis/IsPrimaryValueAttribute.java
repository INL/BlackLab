package nl.inl.blacklab.analysis;

import org.apache.lucene.util.Attribute;

/**
 * A custom attribute indicating if this token value is
 * "primary", that is, the first occurring in the token stream at this position,
 * presumably the original value and the one that will be used to show concordances,
 * or an additional value (that is indexed but may not be stored in the forward index).
 */
public interface IsPrimaryValueAttribute extends Attribute {

    /** Is this the primary value at this token position? */
    boolean isPrimaryValue();

    /**
     * Set whether this is the primary value at this token position.
     *
     * @param b whether or not it is the primary value
     */
    void setPrimaryValue(boolean b);
}
