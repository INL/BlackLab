package nl.inl.blacklab.search.results;

import java.util.function.Consumer;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import nl.inl.blacklab.resultproperty.HitProperty;

/**
 * A HitsInternal implementation that does no locking and can handle up to Integer.MAX_VALUE hits.
 *
 * This means it is safe to fill this object in one thread, then
 * use it from many threads as long as it is not modified anymore.
 */
class HitsInternalNoLock32 implements HitsInternal {

    private class Iterator implements HitsInternal.Iterator {
        private int pos = 0;
        private final EphemeralHit hit = new EphemeralHit();

        public Iterator() {
        }

        @Override
        public boolean hasNext() {
            // Since this iteration method is not thread-safe anyway, use the direct array to prevent repeatedly acquiring the read lock
            return HitsInternalNoLock32.this.docs.size() > this.pos;
        }

        @Override
        public EphemeralHit next() {
            this.hit.doc = HitsInternalNoLock32.this.docs.getInt(pos);
            this.hit.start = HitsInternalNoLock32.this.starts.getInt(pos);
            this.hit.end = HitsInternalNoLock32.this.ends.getInt(pos);
            ++this.pos;
            return this.hit;
        }

        public int doc() {
            return this.hit.doc;
        }
        public int start() {
            return this.hit.start;
        }
        public int end() {
            return this.hit.end;
        }
    }

    protected final IntList docs;
    protected final IntList starts;
    protected final IntList ends;

    HitsInternalNoLock32() {
        this(-1);
    }

    HitsInternalNoLock32(int initialCapacity) {
        if (initialCapacity < 0) {
            // Use default initial capacities
            this.docs = new IntArrayList();
            this.starts = new IntArrayList();
            this.ends = new IntArrayList();
        } else {
            this.docs = new IntArrayList(initialCapacity);
            this.starts = new IntArrayList(initialCapacity);
            this.ends = new IntArrayList(initialCapacity);
        }
    }

    HitsInternalNoLock32(IntList docs, IntList starts, IntList ends) {
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
        docs.add(doc);
        starts.add(start);
        ends.add(end);
    }

    /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
    @Override
    public void add(EphemeralHit hit) {
        docs.add(hit.doc);
        starts.add(hit.start);
        ends.add(hit.end);
    }

    /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
    @Override
    public void add(Hit hit) {
        docs.add(hit.doc());
        starts.add(hit.start());
        ends.add(hit.end());
    }

    public void addAll(HitsInternalNoLock32 hits) {
        docs.addAll(hits.docs);
        starts.addAll(hits.starts);
        ends.addAll(hits.ends);
    }

    public void addAll(HitsInternal hits) {
        hits.withReadLock(__ -> {
            for (EphemeralHit h: hits) {
                docs.add(h.doc);
                starts.add(h.start);
                ends.add(h.end);
            }
        });
    }

    /**
     * Clear the arrays.
     */
    public void clear() {
        docs.clear();
        starts.clear();
        ends.clear();
    }

    @Override
    public void withReadLock(Consumer<HitsInternal> cons) {
        cons.accept(this);
    }

    @Override
    public HitImpl get(long index) {
        return new HitImpl(docs.getInt((int)index), starts.getInt((int)index), ends.getInt((int)index));
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
        h.doc = docs.getInt((int)index);
        h.start = starts.getInt((int)index);
        h.end = ends.getInt((int)index);
    }

    @Override
    public int doc(long index) {
        return this.docs.getInt((int)index);
    }

    @Override
    public int start(long index) {
        return this.starts.getInt((int)index);
    }

    @Override
    public int end(long index) {
        return this.ends.getInt((int)index);
    }

    @Override
    public long size() {
        return docs.size();
    }

    public IntIterator docsIterator() {
        return docs.intIterator();
    }

    /** Note: iterating does not lock the arrays, to do that, it should be performed in a {@link #withReadLock} callback. */
    @Override
    public HitsInternal.Iterator iterator() {
        return new Iterator();
    }

    @Override
    public HitsInternal sort(HitProperty p) {
        int[] indices = new int[docs.size()];
        for (int i = 0; i < indices.length; ++i)
            indices[i] = i;

        IntArrays.quickSort(indices, p::compare);

        HitsInternal r = HitsInternal.create(docs.size(), false, false);
        for (int index : indices) {
            r.add(docs.getInt(index), starts.getInt(index), ends.getInt(index));
        }
        return r;
    }
}
