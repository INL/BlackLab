/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.perdocument;

/**
 * For grouping DocResult objects by the value of a stored field in the Lucene documents. The field
 * name is given when instantiating this class, and might be "author", "year", and such.
 */
public class DocPropertyStoredField extends DocProperty {
	private String fieldName;
	private String friendlyName;

	public DocPropertyStoredField(String fieldName) {
		this(fieldName, fieldName);
	}

	public DocPropertyStoredField(String fieldName, String friendlyName) {
		this.fieldName = fieldName;
		this.friendlyName = friendlyName;
	}

	@Override
	public String get(DocResult result) {
		return result.getDocument().get(fieldName);
	}

	@Override
	public String getName() {
		return friendlyName;
	}

}
