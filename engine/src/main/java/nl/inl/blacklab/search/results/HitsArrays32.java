package nl.inl.blacklab.search.results;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import nl.inl.blacklab.resultproperty.HitProperty;

/**
 * A HitsInternal class that can handle up to Integer.MAX_VALUE
 * hits, but may be faster than HitsArrays.
 */
public class HitsArrays32 implements HitsInternal {

    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    private final IntList docs;
    private final IntList starts;
    private final IntList ends;

    public HitsArrays32() {
        this.docs = new IntArrayList();
        this.starts = new IntArrayList();
        this.ends = new IntArrayList();
    }

    public HitsArrays32(int initialCapacity) {
        this.docs = new IntArrayList(initialCapacity);
        this.starts = new IntArrayList(initialCapacity);
        this.ends = new IntArrayList(initialCapacity);
    }

    public HitsArrays32(IntList docs, IntList starts, IntList ends) {
        if (docs == null || starts == null || ends == null)
            throw new NullPointerException();
        if (docs.size() != starts.size() || docs.size() != ends.size())
            throw new IllegalArgumentException("Passed differently sized hit component arrays to Hits object");

        this.docs = docs;
        this.starts = starts;
        this.ends = ends;
    }

    @Override
    public void add(int doc, int start, int end) {
        this.lock.writeLock().lock();
        docs.add(doc);
        starts.add(start);
        ends.add(end);
        this.lock.writeLock().unlock();
    }

    /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
    @Override
    public void add(Hits.EphemeralHit hit) {
        this.lock.writeLock().lock();
        docs.add(hit.doc);
        starts.add(hit.start);
        ends.add(hit.end);
        this.lock.writeLock().unlock();
    }

    /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
    public void addNoLock(Hits.EphemeralHit hit) {
        docs.add(hit.doc);
        starts.add(hit.start);
        ends.add(hit.end);
    }

    /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
    @Override
    public void add(Hit hit) {
        this.lock.writeLock().lock();
        docs.add(hit.doc());
        starts.add(hit.start());
        ends.add(hit.end());
        this.lock.writeLock().unlock();
    }

    public void addAll(HitsArrays32 hits) {
        this.lock.writeLock().lock();
        hits.withReadLock(c -> {
            docs.addAll(hits.docs);
            starts.addAll(hits.starts);
            ends.addAll(hits.ends);
        });
        this.lock.writeLock().unlock();
    }

    public void addAll(HitsInternal hits) {
        this.lock.writeLock().lock();
        hits.withReadLock(c -> {
            for (Hits.EphemeralHit h: hits) {
                addNoLock(h);
            }
        });
        this.lock.writeLock().unlock();
    }

    /**
     * Clear the arrays.
     */
    public void clear() {
        lock.writeLock().lock();
        docs.clear();
        starts.clear();
        ends.clear();
        lock.writeLock().unlock();
    }

    @Override
    public void withReadLock(Consumer<HitsInternal> cons) {
        lock.readLock().lock();
        cons.accept(this);
        lock.readLock().unlock();
    }

    @Override
    public HitImpl get(long index) {
        lock.readLock().lock();
        HitImpl h = new HitImpl(docs.getInt((int)index), starts.getInt((int)index), ends.getInt((int)index));
        lock.readLock().unlock();
        return h;
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
    public void getEphemeral(long index, Hits.EphemeralHit h) {
        lock.readLock().lock();
        h.doc = docs.getInt((int)index);
        h.start = starts.getInt((int)index);
        h.end = ends.getInt((int)index);
        lock.readLock().unlock();
    }

    @Override
    public int doc(long index) {
        lock.readLock().lock();
        int doc = this.docs.getInt((int)index);
        lock.readLock().unlock();
        return doc;
    }

    @Override
    public int start(long index) {
        lock.readLock().lock();
        int start = this.starts.getInt((int)index);
        lock.readLock().unlock();
        return start;
    }

    @Override
    public int end(long index) {
        lock.readLock().lock();
        int end = this.ends.getInt((int)index);
        lock.readLock().unlock();
        return end;
    }

    @Override
    public long size() {
        lock.readLock().lock();
        int size = docs.size();
        lock.readLock().unlock();
        return size;
    }

    @Override
    public long sizeNoLock() {
        return docs.size();
    }

    @Override
    public int docNoLock(long index) {
        return this.docs.getInt((int)index);
    }

    @Override
    public int startNoLock(long index) {
        return this.starts.getInt((int)index);
    }

    @Override
    public int endNoLock(long index) {
        return this.ends.getInt((int)index);
    }

    public IntIterator docsIterator() {
        return docs.intIterator();
    }

    /** Note: iterating does not lock the arrays, to do that, it should be performed in a {@link #withReadLock} callback. */
    @Override
    public HitIterator iterator() {
        return new HitIterator(this);
    }

    @Override
    public HitsInternal sort(HitProperty p) {
        this.lock.readLock().lock();

        int[] indices = new int[(int)this.size()];
        for (int i = 0; i < indices.length; ++i)
            indices[i] = i;

        IntArrays.quickSort(indices, p::compare);

        HitsArrays32 r = new HitsArrays32();
        Hits.EphemeralHit eph = new Hits.EphemeralHit();
        for (int index : indices) {
            getEphemeral(index, eph);
            r.add(eph);
        }
        this.lock.readLock().unlock();
        return r;
    }
}
