package nl.inl.blacklab.search.results;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * A list of hits, used internally.
 *
 * Includes mutation methods, unlike its parent interface.
 */
public interface HitsInternal extends HitsInternalRead {

    /**
     * Safe maximum size for a Java array.
     *
     * This is JVM-dependent, but the consensus seems to be that
     * this is a safe limit. See e.g.
     * https://stackoverflow.com/questions/3038392/do-java-arrays-have-a-maximum-size
     */
    int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

    /**
     * Create an empty HitsInternal with an initial capacity.
     *
     * @param initialCapacity initial hits capacity, or default if negative
     * @param allowHugeLists if true, the object created can hold more than {@link #MAX_ARRAY_SIZE} hits
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

    HitsInternalRead EMPTY_SINGLETON = new HitsInternalNoLock32();

    void add(int doc, int start, int end);

    void add(EphemeralHit hit);

    void add(Hit hit);

    void addAll(HitsInternalRead hits);

    /**
     * Remove all hits.
     */
    void clear();

}
