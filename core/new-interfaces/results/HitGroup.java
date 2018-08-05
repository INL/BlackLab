package nl.inl.blacklab.interfaces.results;

/** A group of hits. */
public interface HitGroup extends Group<Hit> {
    
    /** Results in this group.
     * 
     * NOTE: we don't always collect all members, so {@link #size()} may return a value
     * larger than <code>members().size().total()</code>.
     *  
     * @return hits in this group */
    @Override
    Hits members();

}
