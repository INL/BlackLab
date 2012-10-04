/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.perdocument;

/**
 * For grouping DocResult objects by the value of a stored field in the Lucene documents. The field
 * name is given when instantiating this class, and might be "author", "year", and such.
 */
public class DocPropertyDecade extends DocProperty {
	private String fieldName;

	public DocPropertyDecade(String fieldName) {
		this.fieldName = fieldName;
	}

	@Override
	public String get(DocResult result) {
		String strYear = result.getDocument().get(fieldName);
		int year = Integer.parseInt(strYear);
		year -= year % 10;
		return Integer.toString(year);
	}

	@Override
	public String getHumanReadable(DocResult result) {
		String strYear = result.getDocument().get(fieldName);
		int year = Integer.parseInt(strYear);
		year -= year % 10;
		return year + "-" + (year + 9);
	}

	@Override
	public String getName() {
		return "decade";
	}
}
