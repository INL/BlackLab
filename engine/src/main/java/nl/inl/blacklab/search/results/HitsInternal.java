package nl.inl.blacklab.search.results;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * A list of hits, used internally.
 *
 * Includes mutation methods, unlike its parent interface.
 */
public interface HitsInternal extends HitsInternalRead {

    /**
     * Create an empty HitsInternal with an initial capacity.
     *
     * @param initialCapacity initial hits capacity, or default if negative
     * @param allowHugeLists if true, the object created can hold more than Integer.MAX_VALUE hits
     * @param mustLock if true, return a locking implementation. If false, implementation may not be locking.
     * @return HitsInternal object
     */
    static HitsInternal create(long initialCapacity, boolean allowHugeLists, boolean mustLock) {
        if (allowHugeLists) {
            // We may have more than Integer.MAX_VALUE hits
            if (mustLock)
                return new HitsInternalLock(initialCapacity);
            return new HitsInternalNoLock(initialCapacity);
        }
        if (initialCapacity > Integer.MAX_VALUE)
            throw new BlackLabRuntimeException("initialCapacity > Integer.MAX_VALUE && !allowHugeLists");
        if (mustLock)
            return new HitsInternalLock32((int)initialCapacity);
        return new HitsInternalNoLock32((int)initialCapacity);
    }

    HitsInternal EMPTY_SINGLETON = new HitsInternalNoLock32();

    void add(int doc, int start, int end);

    void add(EphemeralHit hit);

    void add(Hit hit);

    void addAll(HitsInternalRead hits);

    /**
     * Remove all hits.
     */
    void clear();

}
