package nl.inl.blacklab.search.fimatch;

/** Source of tokens for the forward index matching process. */
public abstract class ForwardIndexDocument {

    /**
     * Return token at specified position.
     *
     * @param annotIndex annotation to read
     * @param pos position to read
     * @return token at this position
     */
    public abstract int getToken(int annotIndex, int pos);

    /**
     * Return string for term id
     * 
     * @param annotIndex annotation for which we want a term string
     * @param termId term id
     * @return corresponding term string
     */
    public abstract String getTermString(int annotIndex, int termId);

    /**
     * Are all the specified term ids equal given these sensitivity settings?
     *
     * @param annotIndex annotation we're looking at
     * @param termId term ids we're comparing
     * @param caseSensitive whether we're comparing case-sensitively
     * @param diacSensitive whether we're comparing diacritics-sensitively
     * @return true if all are equal, false if not
     */
    public abstract boolean termsEqual(int annotIndex, int[] termId, boolean caseSensitive, boolean diacSensitive);

    /**
     * Is this position valid in this document?
     * 
     * @param pos position
     * @return true if valid, false if not
     */
    public abstract boolean validPos(int pos);
}
