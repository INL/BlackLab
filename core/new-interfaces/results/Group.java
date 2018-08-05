package nl.inl.blacklab.interfaces.results;

/**
 * Groups of results
 *
 * @param <Result> result type
 */
public interface Group<Result> {
	
	/** Identity of the group, i.e. the value that was used for grouping.
	 *  
	 * @return group identity */
	ResultPropertyValue identity();
	
	/** Results in this group.
	 * 
	 * NOTE: we don't always collect all members, so {@link #size()} may return a value
	 * larger than <code>members().size().total()</code>.
	 *  
	 * @return hits in this group */
	Results<Result> members();
	
	/**
	 * Total size of this group.
	 * 
	 * We may not always want to collect all members (or even any members),
	 * but we usually do want to know the group size (and this is easily tracked
	 * because we need to look at all results anyway while grouping.
	 *  
	 * @return total size of the group
	 */
	int size();
}
