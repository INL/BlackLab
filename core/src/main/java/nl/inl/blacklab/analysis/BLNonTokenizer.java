package nl.inl.blacklab.analysis;

import org.apache.lucene.analysis.util.CharTokenizer;
import org.apache.lucene.util.AttributeFactory;

/**
 * A tokenizer that doesn't tokenize (returns the whole field value as one
 * token)
 */
public class BLNonTokenizer extends CharTokenizer {

    public BLNonTokenizer(AttributeFactory factory) {
        super(factory);
    }

    public BLNonTokenizer() {
        super();
    }

    @Override
    protected boolean isTokenChar(int c) {
        return true;
    }

}
