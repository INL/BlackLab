package nl.inl.blacklab.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;

/**
 * Analyzer that doesn't tokenize but returns a single token.
 */
public final class BLNonTokenizingAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer source = new BLNonTokenizer();
        TokenStream filter = source;
        filter = new LowerCaseFilter(filter);// lowercase all
        filter = new RemoveAllAccentsFilter(filter); // remove accents
        return new TokenStreamComponents(source, filter);
    }

}
