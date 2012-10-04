/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology.
 * All rights reserved.
 *******************************************************************************/
/**
 *
 */
package nl.inl.blacklab.index.complex;

import org.apache.lucene.analysis.TokenStream;

/**
 * Offers an interface to add a number of TokenFilters to a TokenStream input.
 *
 * Used by ComplexFieldImpl, to allow the calling application control over how the different
 * properties are indexed. Implementations should probably just use anonymous class definitions. See
 * DocIndexerPageXml for examples.
 */
public interface TokenFilterAdder {
	TokenStream addFilters(TokenStream input);
}
