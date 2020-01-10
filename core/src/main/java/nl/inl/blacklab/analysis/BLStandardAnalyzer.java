/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
/**
 *
 */
package nl.inl.blacklab.analysis;

import java.util.Collections;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.LowerCaseFilter;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;

import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;

/**
 * A simple analyzer based on StandardTokenizer that isn't limited to Latin.
 *
 * Has the option of analyzing case-/accent-sensitive or -insensitive, depending
 * on the field name.
 */
public class BLStandardAnalyzer extends Analyzer {

    @Override
    protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer source = new StandardTokenizerFactory(Collections.<String, String>emptyMap()).create();
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
        if (sensitivity != MatchSensitivity.SENSITIVE) {
            // Is this necessary and does it do what we want?
            // e.g. do we want "zon" to ever match "zo'n"? Or are there examples
            //      where this is useful/required?
            filter = new RemovePunctuationFilter(filter); // remove punctuation
        }
        return new TokenStreamComponents(source, filter);
    }
}
