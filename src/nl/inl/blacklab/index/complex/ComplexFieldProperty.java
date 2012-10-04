/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.index.complex;

import java.util.List;

import org.apache.lucene.document.Document;

/**
 * A property in a complex field. See ComplexFieldImpl for details.
 */
interface ComplexFieldProperty {

	public abstract void addValue(String value);

	public abstract void addToLuceneDoc(Document doc, String fieldName, List<Integer> startChars,
			List<Integer> endChars);

	public abstract void clear();

	public abstract void addAlternative(String destName, TokenFilterAdder filterAdder);

	public abstract List<String> getValues();

}
