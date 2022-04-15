/**
 *
 */
package nl.inl.blacklab.index.annotated;

import org.apache.lucene.analysis.TokenStream;

/**
 * Offers an interface to add a number of TokenFilters to a TokenStream input.
 *
 * Used by AnnotatedFieldWriter, to allow the calling application control over how
 * the different properties are indexed. Implementations should probably just
 * use anonymous class definitions. See DocIndexerPageXml for examples.
 */
public interface TokenFilterAdder {
    TokenStream addFilters(TokenStream input);
}
