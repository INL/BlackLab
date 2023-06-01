package nl.inl.blacklab.search.results;

/**
 * Represents the size of the context around a hit.
 * 
 * WARNING: right now, only before() is used (for both before and after context size)!
 * The other functionality will be added in the future.
 */
public class ContextSize {

    /**
     * Get ContexSize.
     *
     * WARNING: right now, only before() is used (for both before and after context size)!
     * The other functionality will be added in the future.
     *
     * @param before context size before hit
     * @param after context size after hit
     * @param includeHit should the hit itself be included in the context or not?
     * @return context size object
     */
    public static ContextSize get(int before, int after, boolean includeHit) {
        return new ContextSize(before, after, includeHit);
    }

    /**
     * Get ContexSize.
     *
     * WARNING: right now, only before() is used (for both before and after context size)!
     * The other functionality will be added in the future.
     *
     * Hit is always included in the context with this method.
     *
     * @param before context size before hit
     * @param after context size after hit
     * @return context size object
     */
    public static ContextSize get(int before, int after) {
        return new ContextSize(before, after, true);
    }

    /**
     * Get ContexSize.
     *
     * The number is used for the context size both before and after the hit.
     *
     * Hit is always included in the context with this method.
     *
     * @param size context size before and after hit
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
        int before = Math.max(a.before, b.before);
        int after = Math.max(a.after, b.after);
        boolean includeHit = a.includeHit || b.includeHit;
        return ContextSize.get(before, after, includeHit);
    }

    private final int before;

    private final int after;

    private final boolean includeHit;

    private ContextSize(int before, int after, boolean includeHit) {
        super();
        this.before = before;
        this.after = after;
        this.includeHit = includeHit;
    }

    public int before() {
        return before;
    }

    public int after() {
        return after;
    }

    @Deprecated
    public int left() {
        return before();
    }

    @Deprecated
	public int right() {
	    return after();
	}
	
	/**
	 * Should the hit itself be included in the context or not?
	 * 
	 * By default it always is, but for some operations you only need
	 * the before context. And to determine collocations you might only want
	 * before and after context and not the hit context itself (because you're
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
        result = prime * result + before;
        result = prime * result + after;
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
        if (before != other.before)            return false;
        return after == other.after;
    }
    
    @Override
    public String toString() {
        return "ContextSize(" + before + ", " + after + ", " + includeHit + ")";
    }

    public boolean isNone() {
        return before == 0 && after == 0;
    }
}
