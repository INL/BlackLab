/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
/**
 *
 */
package nl.inl.blacklab.index.complex;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.TermVector;

/**
 * A property in a complex field. See ComplexFieldImpl for details.
 */
class ComplexFieldPropertyImplSimple implements ComplexFieldProperty {
	protected boolean includeOffsets;

	protected List<String> values = new ArrayList<String>();

	/**
	 * A property may be indexed in different ways (alternatives). This specifies names and filters
	 * for each way.
	 */
	private Map<String, TokenFilterAdder> alternatives = new HashMap<String, TokenFilterAdder>();

	/**
	 * The property name
	 */
	private String propName;

	public ComplexFieldPropertyImplSimple(String name, boolean includeOffsets) {
		this(name, null, includeOffsets);
	}

	public ComplexFieldPropertyImplSimple(String name, TokenFilterAdder filterAdder,
			boolean includeOffsets) {
		super();
		propName = name;
		alternatives.put("", filterAdder);
		this.includeOffsets = includeOffsets;
	}

	@Override
	public void addValue(String value) {
		values.add(value);
	}

	TokenStream getTokenStream(String altName, List<Integer> startChars, List<Integer> endChars) {
		TokenStream ts;
		if (includeOffsets)
			ts = new TokenStreamWithOffsets(values, startChars, endChars);
		else
			ts = new TokenStreamFromList(values);
		TokenFilterAdder filterAdder = alternatives.get(altName);
		if (filterAdder != null)
			return filterAdder.addFilters(ts);
		return ts;
	}

	TermVector getTermVectorOption() {
		return includeOffsets ? TermVector.WITH_POSITIONS_OFFSETS : TermVector.WITH_POSITIONS;
	}

	@Override
	public void addToLuceneDoc(Document doc, String fieldName, List<Integer> startChars,
			List<Integer> endChars) {
		for (String altName : alternatives.keySet()) {
			doc.add(new Field(ComplexFieldUtil.fieldName(fieldName, propName, altName),
					getTokenStream(altName, startChars, endChars), getTermVectorOption()));
		}
	}

	@Override
	public void clear() {
		values.clear();
	}

	@Override
	public void addAlternative(String altName, TokenFilterAdder filterAdder) {
		alternatives.put(altName, filterAdder);
	}

	@Override
	public List<String> getValues() {
		return Collections.unmodifiableList(values);
	}

}
