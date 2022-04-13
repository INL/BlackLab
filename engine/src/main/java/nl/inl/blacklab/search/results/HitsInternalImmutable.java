package nl.inl.blacklab.search.results;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.ints.IntList;
import nl.inl.blacklab.resultproperty.HitProperty;

import java.util.function.Consumer;

/**
 * An immutable implementation of HitsInternal.
 */
public class HitsInternalImmutable implements HitsInternal {

    private class Iterator implements HitsInternal.Iterator {
        private int pos = 0;
        private final Hits.EphemeralHit hit = new Hits.EphemeralHit();

        public Iterator() {
        }

        @Override
        public boolean hasNext() {
            // Since this iteration method is not thread-safe anyway, use the direct array to prevent repeatedly acquiring the read lock
            return HitsInternalImmutable.this.docs.size() > this.pos;
        }

        @Override
        public Hits.EphemeralHit next() {
            this.hit.doc = HitsInternalImmutable.this.docs.getInt(pos);
            this.hit.start = HitsInternalImmutable.this.starts.getInt(pos);
            this.hit.end = HitsInternalImmutable.this.ends.getInt(pos);
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

    private final IntList docs;
    private final IntList starts;
    private final IntList ends;

    public HitsInternalImmutable() {
        this.docs = new IntArrayList(0);
        this.starts = new IntArrayList(0);
        this.ends = new IntArrayList(0);
    }

    public HitsInternalImmutable(IntList docs, IntList starts, IntList ends) {
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
        throw new UnsupportedOperationException("Cannot modify HitsInternalImmutable");
    }

    /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
    @Override
    public void add(Hits.EphemeralHit hit) {
        throw new UnsupportedOperationException("Cannot modify HitsInternalImmutable");
    }

    /** Add the hit to the end of this list, copying the values. The hit object itself is not retained. */
    @Override
    public void add(Hit hit) {
        throw new UnsupportedOperationException("Cannot modify HitsInternalImmutable");
    }

    public void addAll(HitsArrays32 hits) {
        throw new UnsupportedOperationException("Cannot modify HitsInternalImmutable");
    }

    public void addAll(HitsInternal hits) {
        throw new UnsupportedOperationException("Cannot modify HitsInternalImmutable");
    }

    /**
     * Clear the arrays.
     */
    public void clear() {
        throw new UnsupportedOperationException("Cannot add to immutable HitsInternal");
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
    public void getEphemeral(long index, Hits.EphemeralHit h) {
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
        int[] indices = new int[(int)this.size()];
        for (int i = 0; i < indices.length; ++i)
            indices[i] = i;

        IntArrays.quickSort(indices, p::compare);

        Hits.EphemeralHit eph = new Hits.EphemeralHit();
        IntList sDocs = new IntArrayList(indices.length);
        IntList sStarts = new IntArrayList(indices.length);
        IntList sEnds = new IntArrayList(indices.length);
        for (int index : indices) {
            sDocs.add(docs.getInt(index));
            sStarts.add(starts.getInt(index));
            sEnds.add(ends.getInt(index));
        }
        return new HitsInternalImmutable(sDocs, sStarts, sEnds);
    }
}
