package nl.inl.blacklab.search.textpattern;

/**
 * AND operation.
 * 
 * Actually just TextPatternAndNot without the option of specifying a NOT part.
 */
public class TextPatternAnd extends TextPatternAndNot {

    public TextPatternAnd(TextPattern... clauses) {
        super(clauses);
    }
}
