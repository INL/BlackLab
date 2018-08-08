package nl.inl.blacklab.interfaces.results;

import nl.inl.blacklab.interfaces.index.Doc;

/**
 * Represents a single search hit from a Hits object.
 * 
 * Can be implemented either as a thin shell that delegates to a hit in a Hits object,
 * or as an object that stores the hit's information for further processing
 * (e.g. so we can sort hits).
 * 
 * A sequential Hits implementation will likely want to avoid instantiating a Hit 
 * object for each Hit it sees, but will instead re-use a Hit instance that queries 
 * itself about the "current hit".
 *  
 * For this reason, Hit objects supplied by BlackLab should be normally assumed 
 * to be ephemeral. If you want to store the hit and use it later, you must call 
 * save() to get an immutable copy.
 */
public interface Hit {
	
	/**
	 * Returns an immutable copy of this hit.
	 * 
	 * If you want to store a hit, you must call this method first
	 * to acquire an immutable copy of it.
	 * 
	 * The reason is that all Hit objects supplied by BlackLab should be 
	 * assumed to be ephemeral (see above).
	 * 
	 * (already-immutable Hit instances will not create a copy, but simply 
	 *  return themselves)
	 * 
	 * @return an immutable copy of this hit
	 */
	Hit save();
	
	/** 
	 * Get the document id.
	 * 
	 * NOTE: where performance matters, favour this method over doc().id().
	 *  
	 * @return id of the document this hit occurs in
	 */
	int doc();
	
	/**
	 * Get the document.
	 * 
	 * NOTE: where performance matters, favour docId() over doc().id().
	 * 
	 * @return The document this hit occurs in. */
	Doc docObj();
	
	/** @return First word of this hit in the document. */
	int start();
	
	/** @return First word after this hit in the document. */
	int end();
	
	/** @return the number of captured groups */
	int groupCount();
	
	/** Start position of the specified group
	 * @param groupNumber 0-based capture group number
	 * @return start position
	 */
	int groupStart(int groupNumber);
	
	/** End position of the specified group
	 * @param groupNumber 0-based capture group number
	 * @return end position
	 */
	int groupEnd(int groupNumber);
	
}
