/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
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
