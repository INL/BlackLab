package nl.inl.blacklab.search.results;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.ints.IntList;
import nl.inl.blacklab.resultproperty.HitProperty;

/**
 * A HitsInternal implementation that locks and can handle up to Integer.MAX_VALUE.
 *
 * A test calling {@link #add(int, int, int)} millions of times came out to be about
 * 19% faster than {@link HitsInternalLock}. Iteration is about 10x faster.
 *
 * Those percentages are not representative of real-world usage of course, but on
 * huge resultsets this will likely save a few seconds.
 */
class HitsInternalLock32 extends HitsInternalNoLock32 {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    HitsInternalLock32(int initialCapacity) {
        super(initialCapacity);
    }

    HitsInternalLock32(IntList docs, IntList starts, IntList ends) {
        super(docs, starts, ends);
    }

    @Override
    public void add(int doc, int start, int end) {
        this.lock.writeLock().lock();
        try {
            super.add(doc, start, end);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
    @Override
    public void add(EphemeralHit hit) {
        this.lock.writeLock().lock();
        try {
            super.add(hit);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
    @Override
    public void add(Hit hit) {
        this.lock.writeLock().lock();
        try {
            super.add(hit);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void addAll(HitsInternalLock32 hits) {
        this.lock.writeLock().lock();
        try {
            super.addAll(hits);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    public void addAll(HitsInternal hits) {
        this.lock.writeLock().lock();
        try {
            super.addAll(hits);
        } finally {
            this.lock.writeLock().unlock();
        }
    }

    /**
     * Clear the arrays.
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            super.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void withReadLock(Consumer<HitsInternal> cons) {
        lock.readLock().lock();
        try {
            super.withReadLock(cons);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public HitImpl get(long index) {
        lock.readLock().lock();
        try {
            return super.get(index);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Copy values into the ephemeral hit, for use in a hot loop or somesuch.
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
     */
    @Override
    public void getEphemeral(long index, EphemeralHit h) {
        lock.readLock().lock();
        try {
            super.getEphemeral(index, h);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int doc(long index) {
        lock.readLock().lock();
        try {
            return super.doc(index);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int start(long index) {
        lock.readLock().lock();
        try {
            return super.start(index);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public int end(long index) {
        lock.readLock().lock();
        try {
            return super.end(index);
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long size() {
        lock.readLock().lock();
        try {
            return super.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public HitsInternal sort(HitProperty p) {
        this.lock.readLock().lock();
        try {
            return super.sort(p);
        } finally {
            this.lock.readLock().unlock();
        }
    }
}
