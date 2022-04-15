/**
 *
 */
package nl.inl.blacklab.analysis;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;

import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * Simple whitespace analyzer.
 *
 * Has the option of analyzing case-/accent-sensitive or -insensitive, depending
 * on the field name.
 */
public final class BLWhitespaceAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer source = new WhitespaceTokenizer();
        TokenStream filter = source;
        MatchSensitivity sensitivity = MatchSensitivity.INSENSITIVE;
        if (AnnotatedFieldNameUtil.isAnnotatedField(fieldName))
            sensitivity = AnnotatedFieldNameUtil.sensitivity(fieldName);
        if (!sensitivity.isCaseSensitive()) {
            filter = new LowerCaseFilter(filter);// lowercase all
        }
        if (!sensitivity.isDiacriticsSensitive()) {
            filter = new RemoveAllAccentsFilter(filter); // remove accents
        }
        return new TokenStreamComponents(source, filter);
    }
}
