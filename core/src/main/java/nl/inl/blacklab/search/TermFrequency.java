package nl.inl.blacklab.search;

import nl.inl.blacklab.search.results.Result;

/**
 * One token and its frequency in some context.
 */
public class TermFrequency implements Result<TermFrequency> {

    /** What token this frequency is for */
    public String term;

    /** How many times the token occurs in the context */
    public long frequency;

    /**
     * Construct a collocation
     * 
     * @param token a token (word, lemma, pos, etc.)
     * @param frequency the token's frequency in the context
     */
    public TermFrequency(String token, int frequency) {
        super();
        this.term = token;
        this.frequency = frequency;
    }

    @Override
    public String toString() {
        return term + " (" + frequency + ")";
    }

    /**
     * Natural ordering of TokenFrequency is by decreasing frequency.
     */
    @Override
    public int compareTo(TermFrequency o) {
        long delta = o.frequency - frequency;
        return delta == 0 ? 0 : (delta < 0 ? -1 : 1);
    }

    @Override
    public int hashCode() {
        return (int) (term.hashCode() + frequency);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj instanceof TermFrequency) {
            return ((TermFrequency) obj).term.equals(term) && ((TermFrequency) obj).frequency == frequency;
        }
        return false;
    }

}
