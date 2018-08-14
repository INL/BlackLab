package nl.inl.blacklab.search.results;

import java.util.Iterator;

import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.util.ThreadPauser;

/**
 * A list of results of some type.
 *
 * @param <T> result type, e.g. Hit
 */
public abstract class Results<T> implements Iterable<T> {

    /** Id the next Hits instance will get */
    private static int nextHitsObjId = 0;

    private static synchronized int getNextHitsObjId() {
        return nextHitsObjId++;
    }
    
    /** Unique id of this Hits instance (for debugging) */
    protected final int hitsObjId = getNextHitsObjId();
    
    /** Information about the original query: index, field, max settings, max stats. */
    private QueryInfo queryInfo;
    
    /**
     * Helper object for pausing threads (making sure queries
     * don't hog the CPU for way too long).
     */
    protected ThreadPauser threadPauser;
    
    public Results(QueryInfo queryInfo) {
        this.queryInfo = queryInfo;
        if (queryInfo.resultsObjectId() < 0)
            queryInfo.setResultsObjectId(hitsObjId); // We're the original query. set the id.
        threadPauser = new ThreadPauser();
    }

    /**
     * Get information about the original query.
     * 
     * This includes the index, field, max. settings, and max. stats
     * (whether the max. settings were reached).
     * 
     * @return query info
     */
    public QueryInfo queryInfo() {
        return queryInfo;
    }

    /**
     * Get the field these hits are from.
     * 
     * @return field
     */
    public AnnotatedField field() {
        return queryInfo().field();
    }

    /**
     * Get the index these hits are from.
     * 
     * @return index
     */
    public BlackLabIndex index() {
        return queryInfo().index();
    }
    
    public int resultsObjId() {
        return hitsObjId;
    }

    public ThreadPauser threadPauser() {
        return threadPauser;
    }

    /**
     * Is this a hits window?
     * 
     * @return true if it's a window, false if not
     */
    public boolean isWindow() {
        return windowStats() != null;
    }

    /**
     * If this is a hits window, return the window stats.
     * 
     * @return window stats, or null if this is not a hits window
     */
    public WindowStats windowStats() {
        return null;
    }

    /**
     * Is this sampled from another instance?
     * 
     * @return true if it's a sample, false if not
     */
    public boolean isSample() {
        return sampleParameters() != null;
    }

    /**
     * If this is a sample, return the sample parameters.
     * 
     * Also includes the explicitly set or randomly chosen seed. 
     * 
     * @return sample parameters, or null if this is not a sample
     */
    public SampleParameters sampleParameters() {
        return null;
    }

    /**
     * For Hits, this is an alias of hitsProcessedTotal.
     * 
     * Other Results classes each have size(), but the meaning depends on the type of results.
     * 
     * @return number of hits processed total
     */
    public abstract int size();

    /**
     * Return the specified hit.
     *
     * @param i index of the desired hit
     * @return the hit, or null if it's beyond the last hit
     */
    public abstract T get(int i);

    /**
     * Return an iterator over these hits.
     *
     * The order is the sorted order, not the original order. Use
     * hitsInOriginalOrder() to iterate in the original order.
     *
     * @return the iterator
     */
    @Override
    public abstract Iterator<T> iterator();

    @Override
    public abstract String toString();

//    /**
//     * Group these hits by a criterium (or several criteria).
//     *
//     * @param criteria the hit property to group on
//     * @return a HitGroups object representing the grouped hits
//     */
//    public abstract ResultGroups<T> groupedBy(ResultProperty<T> criteria);
//
//    /**
//     * Select only the hits where the specified property has the specified value.
//     * 
//     * @param property property to select on, e.g. "word left of hit"
//     * @param value value to select on, e.g. 'the'
//     * @return filtered hits
//     */
//    public abstract Results<T> filteredBy(ResultProperty<T> property, PropertyValue value);
//
//    /**
//     * Return a new Hits object with these hits sorted by the given property.
//     *
//     * This keeps the existing sort (or lack of one) intact and allows you to cache
//     * different sorts of the same resultset. The hits themselves are reused between
//     * the two Hits instances, so not too much additional memory is used.
//     *
//     * @param sortProp the hit property to sort on
//     * @return a new Hits object with the same hits, sorted in the specified way
//     */
//    public abstract Results<T> sortedBy(ResultProperty<T> sortProp);

    /**
     * Get a window into this list of hits.
     *
     * Use this if you're displaying part of the resultset, like in a paging
     * interface. It makes sure BlackLab only works with the hits you want to
     * display and doesn't do any unnecessary processing on the other hits.
     *
     * HitsWindow includes methods to assist with paging, like figuring out if there
     * hits before or after the window.
     *
     * @param first first hit in the window (0-based)
     * @param windowSize size of the window
     * @return the window
     */
    public abstract Results<T> window(int first, int windowSize);

}
