package nl.inl.blacklab.interfaces.index;

/** Handle to a document in the content store. */
public interface ContentStoreDoc {
	
	/**
	 * Returns the document length in characters
	 * @return the length in characters
	 */
	int length();
	
	/**
	 * Retrieve the entire document.
	 * @return document content
	 */
	String contents();
	
	/**
	 * Retrieve part of the document.
	 *
	 * (NOTE: this is really the reason for {@link ContentStore} to exist in the
	 * first place; if we didn't sometimes need to get parts of a huge document,
	 * storing documents in Lucene fields would probably work just as well)
	 * 
	 * @param startChar first character to retrieve
	 * @param endChar character after the last character to retrieve
	 * @return partial document content
	 */
	String contents(int startChar, int endChar);
	
	// Read/write-mode only methods
	//-----------------------------------------------------
	
	/**
	 * Delete document from the content store.
	 */
	void delete();
	
	/**
	 * Append part of the contents to this document.
	 *
	 * You can call this several times. Finish with a call to store() 
	 * to actually store the entire document.
	 *
	 * @param partialContent part of the content of the document to store
	 */
	void appendContents(String partialContent);
	
	/**
	 * Finish storing this document.
	 */
	void store();
	
}