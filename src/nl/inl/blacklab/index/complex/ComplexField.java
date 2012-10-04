/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.index.complex;

import java.io.IOException;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;

/**
 * A complex field is like a Lucene field, but in addition to its "normal" value, it can have
 * multiple properties per word (not just a single token). The properties might be "headword", "pos"
 * (part of speech), "namedentity" (whether or not the word is (part of) a named entity like a
 * location or place), etc.
 *
 * Complex fields are implemented by indexing a field in Lucene for each property. For example, if
 * complex field "contents" has properties "headword" and "pos", there would be 3 Lucene fields for
 * the complex field: "contents", "contents__headword" and "contents__pos".
 *
 * The main field ("contents" in the above example) may include offset information if you want (e.g.
 * for highlighting). All Lucene fields will include position information (for use with
 * SpanQueries).
 *
 * N.B. It is crucial that you call addValue() for every token. Also, make sure you call
 * addPropertyValue() for EVERY property EVERY token. The same goes for addStartChar() and
 * addEndChar() (although, if you don't want any offsets, you need not call these).
 */
public interface ComplexField {
	public abstract int numberOfTokens();

	public abstract void addProperty(String name, TokenFilterAdder filterAdder);

	public abstract void addPropertyAlternative(String sourceName, String altPostfix);

	public abstract void addPropertyAlternative(String sourceName, String altPostfix,
			TokenFilterAdder filterAdder);

	public abstract void addProperty(String name);

	public abstract void addValue(String value);

	public abstract void addStartChar(int startChar);

	public abstract void addEndChar(int endChar);

	public abstract void addPropertyValue(String name, String value);

	public abstract void addToLuceneDoc(Document doc);

	public abstract void clear();

	public abstract void addAlternative(String altPostfix);

	public abstract void addAlternative(String altPostfix, TokenFilterAdder filterAdder);

	public abstract void addTokens(TokenStream c) throws IOException;

	public abstract void addPropertyTokens(String propertyName, TokenStream c) throws IOException;

	public abstract List<String> getPropertyValues(String name);
}
