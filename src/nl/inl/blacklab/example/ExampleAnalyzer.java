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
package nl.inl.blacklab.example;

import java.io.Reader;

import nl.inl.blacklab.filter.RemoveAllAccentsFilter;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.util.Version;

/**
 * The analyzer we use for the simple fields of the data.
 *
 * Has the option of analyzing case-/accent-sensitive or -insensitive, depending on the field name.
 */
public final class ExampleAnalyzer extends Analyzer {

	@Override
	public TokenStream tokenStream(String fieldName, Reader reader) {

		// Base our analyzer on the StandardTokenizer
		TokenStream ts = new StandardTokenizer(Version.LUCENE_30, reader);

		// Do we want case-sensitive analysis?
		if (!ComplexFieldUtil.getAlternative(fieldName).equals("s")) {

			// No; desensitize input
			ts = new LowerCaseFilter(Version.LUCENE_36, ts); // lowercase all
			ts = new RemoveAllAccentsFilter(ts); // remove accents

		}
		return ts;
	}

}
