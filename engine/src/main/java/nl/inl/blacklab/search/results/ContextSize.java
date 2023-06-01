package nl.inl.blacklab.search.results;

/**
 * Represents the size of the context around a hit.
 * 
 * WARNING: right now, only left() is used (for both left and right context size)!
 * The other functionality will be added in the future.
 */
public class ContextSize {

    /**
     * Get ContexSize.
     *
     * WARNING: right now, only left() is used (for both left and right context size)!
     * The other functionality will be added in the future.
     *
     * @param left left context size
     * @param right right context size
     * @param includeHit should the hit itself be included in the context or not?
     * @return context size object
     */
    public static ContextSize get(int left, int right, boolean includeHit) {
        return new ContextSize(left, right, includeHit);
    }

    /**
     * Get ContexSize.
     *
     * WARNING: right now, only left() is used (for both left and right context size)!
     * The other functionality will be added in the future.
     *
     * Hit is always included in the context with this method.
     *
     * @param left left context size
     * @param right right context size
     * @return context size object
     */
    public static ContextSize get(int left, int right) {
        return new ContextSize(left, right, true);
    }

    /**
     * Get ContexSize.
     *
     * The number is used for the context size both to the left and the right.
     *
     * Hit is always included in the context with this method.
     *
     * @param size left and right context size
     * @return context size object
     */
    public static ContextSize get(int size) {
        return new ContextSize(size, size, true);
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
        int right = Math.max(a.right, b.right);
        boolean includeHit = a.includeHit || b.includeHit;
        return ContextSize.get(left, right, includeHit);
    }

    private final int left;

    private final int right;

    private final boolean includeHit;

    private ContextSize(int left, int right, boolean includeHit) {
        super();
        this.left = left;
        this.right = right;
        this.includeHit = includeHit;
    }

    public int left() {
        return left;
    }

	public int right() {
	    return right;
	}
	
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
	public boolean includeHit() {
	    return includeHit;
	}

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (includeHit ? 1231 : 1237);
        result = prime * result + left;
        result = prime * result + right;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        ContextSize other = (ContextSize) obj;
        if (includeHit != other.includeHit)
            return false;
        if (left != other.left)            return false;
        return right == other.right;
    }
    
    @Override
    public String toString() {
        return "ContextSize(" + left + ", " + right + ", " + includeHit + ")";
    }
}
