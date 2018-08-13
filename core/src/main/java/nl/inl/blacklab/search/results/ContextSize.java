package nl.inl.blacklab.search.results;

import nl.inl.blacklab.search.BlackLabIndex;

/**
 * Represents the size of the context around a hit.
 */
public class ContextSize {
    
//    public static ContextSize get(int left, int right, boolean includeHit) {
//        return new ContextSize(left, right, includeHit);
//    }
//	
//    public static ContextSize get(int left, int right) {
//        return new ContextSize(left, right);
//    }
    
    public static ContextSize get(int size) {
        return new ContextSize(size);
    }
    
    public static ContextSize hitOnly() {
        return get(0);
    }

    public static ContextSize defaultSize() {
        return BlackLabIndex.DEFAULT_CONTEXT_SIZE;
    }
    
	private int left;
	
//    private int right;
//    
//    private boolean includeHit;
    
//    ContextSize(int left, int right, boolean includeHit) {
//        super();
//        this.left = left;
//        this.right = right;
//        this.includeHit = includeHit;
//    }
//
//    ContextSize(int left, int right) {
//        this(left, right, true);
//    }

    ContextSize(int size) {
        this.left = size;
    }

    public int left() {
	    return left;
	}

    /**
     * Return the minimal context size that encompasses both parameters. 
     * 
     * @param a first context size
     * @param b second context size
     * @return union of both context sizes
     */
    public static ContextSize union(ContextSize a, ContextSize b) {
        int left = Math.max(a.left, b.left);
        return ContextSize.get(left);
//        int right = Math.max(a.right, b.right);
//        boolean includeHit = a.includeHit || b.includeHit;
//        return ContextSize.get(left, right, includeHit);
    }

//	public int right() {
//	    return right;
//	}
//	
//	/**
//	 * Should the hit itself be included in the context or not?
//	 * 
//	 * By default it always is, but for some operations you only need
//	 * the left context. And to determine collocations you might only want
//	 * left and right context and not the hit context itself (because you're
//	 * counting words that occur around the hit)
//	 * 
//	 * @return whether or not the hit context should be included
//	 */
//	public boolean includeHit() {
//	    return includeHit;
//	}

}
