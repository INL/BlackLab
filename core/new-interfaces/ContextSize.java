package nl.inl.blacklab.interfaces;

/**
 * Represents the size of the context around a hit.
 */
interface ContextSize {
	
	int left();

	int right();
	
	/**
	 * Should the hit itself be included in the context or not?
	 * 
	 * By default it always is, but for some operations you only need
	 * the left context. And to determine collocations you might only want
	 * left and right context and not the hit context itself (because you're
	 * counting words that occur around the hit)
	 * 
	 * @return whether or not the hit context should be included
	 */
	default boolean includeHit() {
	    return true;
	}

}
