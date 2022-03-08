package nl.inl.blacklab.search.results;

import java.util.function.Consumer;

import it.unimi.dsi.fastutil.ints.IntIterator;
import nl.inl.blacklab.resultproperty.HitProperty;

/**
 * A read-only interface to a list of hits.
 */
public interface HitsInternalRead extends Iterable<EphemeralHit> {
    /**
     * Perform an operation with read lock.
     * <p>
     * If the implementation doesn't support locking, it will simply
     * perform the operation without it.
     *
     * @param cons operation to perform
     */
    void withReadLock(Consumer<HitsInternal> cons);

    /**
     * Get a Hit object.
     * <p>
     * Avoid this method if possible, as it instantiates an object.
     *
     * @param index hit index
     * @return hit object
     */
    Hit get(long index);

    /**
     * Get an ephemeral Hit object.
     * <p>
     * Writes the doc, start and end values to the specified mutable
     * hit object.
     * <p>
     * Useful in a hot loop or somesuch.
     * The intent of this function is to allow retrieving many hits without needing to allocate so many short lived objects.
     * Example:
     *
     * <pre>
     * EphemeralHitImpl h = new EphemeralHitImpl();
     * int size = hits.size();
     * for (int i = 0; i < size; ++i) {
     *     hits.getEphemeral(i, h);
     *     // use h now
     * }
     * </pre>
     *
     * @param index hit index
     * @param h     hit object
     */
    void getEphemeral(long index, EphemeralHit h);

    /**
     * Get the doc id for a hit.
     *
     * @param index hit index
     * @return doc id
     */
    int doc(long index);

    /**
     * Get the start position for a hit.
     *
     * @param index hit index
     * @return start position
     */
    int start(long index);

    /**
     * Get the end position for a hit.
     *
     * @param index hit index
     * @return end position
     */
    int end(long index);

    long size();

    /**
     * Iterate over the doc ids of the hits.
     * <p>
     * NOTE: iterating does not lock the arrays, to do that,
     * it should be performed in a {@link #withReadLock} callback.
     *
     * @return iterator over the doc ids
     */
    IntIterator docsIterator();

    /**
     * Iterate over the hits.
     * <p>
     * NOTE: iterating does not lock the arrays, to do that,
     * it should be performed in a {@link #withReadLock} callback.
     *
     * @return iterator
     */
    @Override
    Iterator iterator();

    /**
     * Return a new object with sorted hits.
     *
     * @param p sort property
     * @return sorted hits
     */
    HitsInternal sort(HitProperty p);

    /**
     * For iterating through the hits using EphemeralHit
     */
    interface Iterator extends java.util.Iterator<EphemeralHit> {

    }
}
