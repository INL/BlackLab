/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
package nl.inl.blacklab.perdocument;

import nl.inl.util.Utilities;

public class DocGroupPropertyIdentity extends DocGroupProperty {
	@Override
	public String get(DocGroup result) {
		return Utilities.sanitizeForSorting(result.getIdentity());
	}
}
