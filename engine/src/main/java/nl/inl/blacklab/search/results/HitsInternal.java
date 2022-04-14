package nl.inl.blacklab.search.results;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * A list of simple hits, used internally.
 *
 * Contrary to {@link Hits}, this only contains doc, start and end
 * for each hit, so no captured groups information, and no other
 * bookkeeping (hit/doc retrieved/counted stats, hasAscendingLuceneDocIds, etc.).
 *
 * Includes mutation methods, unlike its parent interface.
 */
public interface HitsInternal extends HitsInternalRead {

    /**
     * Create an empty HitsInternal with an initial capacity.
     *
     * @param initialCapacity initial hits capacity, or default if negative
     * @param allowHugeLists if true, the object created can hold more than {@link HitsInternalRead#MAX_ARRAY_SIZE} hits
     * @param mustLock if true, return a locking implementation. If false, implementation may not be locking.
     * @return HitsInternal object
     */
    static HitsInternal create(long initialCapacity, boolean allowHugeLists, boolean mustLock) {
        return create(initialCapacity, allowHugeLists ? Long.MAX_VALUE : MAX_ARRAY_SIZE, mustLock);
    }

    static HitsInternal create(long initialCapacity, long maxCapacity, boolean mustLock) {
        if (maxCapacity > MAX_ARRAY_SIZE) {
            if (mustLock)
                return new HitsInternalLock(initialCapacity);
            return new HitsInternalNoLock(initialCapacity);
        }
        if (initialCapacity > MAX_ARRAY_SIZE)
            throw new BlackLabRuntimeException("initialCapacity=" + initialCapacity + " > " + MAX_ARRAY_SIZE + " && !allowHugeLists");
        if (mustLock)
            return new HitsInternalLock32((int)initialCapacity);
        return new HitsInternalNoLock32((int)initialCapacity);
    }

    void add(int doc, int start, int end);

    void add(EphemeralHit hit);

    void add(Hit hit);

    void addAll(HitsInternalRead hits);

    /**
     * Remove all hits.
     */
    void clear();

}
