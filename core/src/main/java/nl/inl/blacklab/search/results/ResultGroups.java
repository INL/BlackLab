package nl.inl.blacklab.search.results;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

public interface ResultGroups {
    /**
     * Get the total number of results that were grouped
     *
     * @return the number of results that were grouped
     */
    int getTotalResults();

    /**
     * Return the size of the largest group
     *
     * @return size of the largest group
     */
    int getLargestGroupSize();

    /**
     * Return the number of groups
     *
     * @return number of groups
     */
    int numberOfGroups();
    
    /**
     * Get our original query info.
     * 
     * @return query info
     */
    QueryInfo queryInfo();

    /**
     * Get index our results came from.
     * 
     * @return index
     */
    default BlackLabIndex index() {
        return queryInfo().index();
    }

    /**
     * Get field our results came from.
     * 
     * If this is a set of document results that didn't come from a set of hits,
     * this will return null.
     * 
     * @return field, or null if none
     */
    default AnnotatedField field() {
        return queryInfo().field();
    }

}
