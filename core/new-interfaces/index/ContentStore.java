package nl.inl.blacklab.interfaces.index;

/**
 * A store for large documents that allows us to fetch partial documents
 * by character offsets.
 *
 * (ContentStore exists specifically because of that need to sometimes fetch
 *  parts of documents efficiently; without that, storing large documents in 
 *  Lucene fields would probably be fine too)
 */
public interface ContentStore extends DocStore<ContentStoreDoc> {

    // (no additional methods)
	
}
