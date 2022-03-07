package nl.inl.blacklab.search.results;

import java.util.Iterator;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.ints.IntIterator;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.resultproperty.HitProperty;

public interface HitsInternal extends Iterable<Hits.EphemeralHit> {

    interface Iterator extends java.util.Iterator<Hits.EphemeralHit> {

    }

    /**
     * Create a HitsInternal with an initial and maximum capacity.
     *
     * Maximum capacity is only used to decide which implementation to use
     * and the actual maximum capacity may be greater.
     *
     * @param initialCapacity initial hits capacity
     * @param maxCapacity maximum number of hits
     * @return HitsInternal object
     */
    static HitsInternal create(long initialCapacity, long maxCapacity) {
        return create(initialCapacity, maxCapacity > Integer.MAX_VALUE);
    }

    /**
     * Create a HitsInternal with a fixed capacity.
     *
     * Capacity is not actually fixed, it is the initial capacity and it
     * is also used to decide which implementation to use.
     *
     * It is good practice to only use createFixed if you know it really
     * is a fixed size. Otherwise use {@link #create(long, boolean)}.
     *
     * @param capacity fixed capacity
     * @return HitsInternal object
     */
    static HitsInternal createFixed(long capacity) {
        return create(capacity, capacity > Integer.MAX_VALUE);
    }

    /**
     * Create an empty HitsInternal that can hold up to Long.MAX_VALUE items.
     *
     * Use this if you know for sure you have more than Integer.MAX_VALUE hits.
     * If you don't know how many hits you'll have, use {@link #create()} instead.
     *
     * @return HitsInternal object
     */
    static HitsInternal createHuge() {
        return create(true);
    }

    /**
     * Create an empty HitsInternal that can hold up to Integer.MAX_VALUE items.
     *
     * @return HitsInternal object
     */
    static HitsInternal createSmall() {
        return create(false);
    }

    /**
     * Create an empty HitsInternal.
     *
     * @param allowHugeLists if true, the object created can hold more than Integer.MAX_VALUE hits
     * @return HitsInternal object
     */
    static HitsInternal create(boolean allowHugeLists) {
        return create(-1, allowHugeLists);
    }

    /**
     * Create an empty HitsInternal.
     *
     * This does the same as {@link #createHuge()}, but should be used
     * in cases where you simply don't know how many hits there are going to
     * be. Use {@link #createHuge()} if you know for sure you need it.
     *
     * @return HitsInternal object
     */
    static HitsInternal create() {
        return create(true);
    }

    /**
     * Create an empty HitsInternal with an initial capacity.
     *
     * @param initialCapacity initial hits capacity, or default if negative
     * @param allowHugeLists if true, the object created can hold more than Integer.MAX_VALUE hits
     * @return HitsInternal object
     */
    static HitsInternal create(long initialCapacity, boolean allowHugeLists) {
        if (initialCapacity < 0)
            return allowHugeLists ? new Hits.HitsArrays() : new HitsArrays32();
        if (initialCapacity > Integer.MAX_VALUE && !allowHugeLists)
            throw new BlackLabRuntimeException("initialCapacity > Integer.MAX_VALUE && !allowHugeLists");
        return allowHugeLists ? new Hits.HitsArrays(initialCapacity) : new HitsArrays32((int)initialCapacity);
    }

    HitsInternal EMPTY_SINGLETON = new HitsInternalImmutable();

    void add(int doc, int start, int end);

    void add(Hits.EphemeralHit hit);

    void add(Hit hit);

    void addAll(HitsInternal hits);

    void withReadLock(Consumer<HitsInternal> cons);

    Hit get(long index);

    void getEphemeral(long index, Hits.EphemeralHit h);

    int doc(long index);

    int start(long index);

    int end(long index);

    long size();

    IntIterator docsIterator();

    @Override
    HitsInternal.Iterator iterator();

    HitsInternal sort(HitProperty p);

    void clear();

}
