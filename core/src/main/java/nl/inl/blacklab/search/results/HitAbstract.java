package nl.inl.blacklab.search.results;

/**
 * Abstract Hit implementation that provides equals(), hashCode() and toString.
 * 
 * NOTE: even though equals() and hashCode() are defined, please note that some
 * Hit objects are mutable and cannot be used as e.g. keys to a Map. 
 */
public abstract class HitAbstract implements Hit {

    @Override
    public boolean equals(Object with) {
        if (this == with)
            return true;
        if (with instanceof Hit) {
            Hit o = (Hit) with;
            return doc() == o.doc() && start() == o.start() && end() == o.end();
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format("doc %d, words %d-%d", doc(), start(), end());
    }
    
    @Override
    public int hashCode() {
        return (doc() * 17 + start()) * 31 + end();
    }
    
    // POSSIBLE FUTURE OPTIMIZATION

//    /**
//     * Cached hash code, or Integer.MIN_VALUE if not calculated yet.
//     * 
//     * Can help when using Hit as a key in HashMap, e.g. in CapturedGroups 
//     * and possibly with Contexts in the future.
//     * 
//     * Does cost about 17% extra memory for Hit objects. 
//     */
//    private int hashCode = Integer.MIN_VALUE;
//    
//    @Override
//    public int hashCode() {
//        if (hashCode == Integer.MIN_VALUE) {
//            hashCode = (doc() * 17 + start()) * 31 + end();
//        }
//        return hashCode;
//    }
    
}
