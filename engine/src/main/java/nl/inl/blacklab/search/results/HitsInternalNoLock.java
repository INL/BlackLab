package nl.inl.blacklab.search.results;

import java.util.function.Consumer;

import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntBigList;
import it.unimi.dsi.fastutil.ints.IntIterator;
import it.unimi.dsi.fastutil.longs.LongBigArrays;
import nl.inl.blacklab.resultproperty.HitProperty;

/**
 * A HitsInternal implementation that does no locking and can handle huge result sets.
 *
 * This means it is safe to fill this object in one thread, then
 * use it from many threads as long as it is not modified anymore.
 *
 * A test calling {@link #add(int, int, int)} millions of times came out to be about 11% faster than
 * {@link HitsInternalLock}. That is not representative of real-world usage of course, but on huge
 * resultsets this will likely save a few seconds.
 */
class HitsInternalNoLock implements HitsInternal {

    /**
     * Class to iterate over hits.
     *
     * NOTE: contrary to expectation, implementing this class using iterators
     * over docs, starts and ends makes it slower.
     */
    private class HitIterator implements Iterator {
        private int pos = 0;

        private final EphemeralHit hit = new EphemeralHit();

        public HitIterator() {
        }

        @Override
        public boolean hasNext() {
            return HitsInternalNoLock.this.docs.size64() > this.pos;
        }

        @Override
        public EphemeralHit next() {
            this.hit.doc = HitsInternalNoLock.this.docs.getInt(pos);
            this.hit.start = HitsInternalNoLock.this.starts.getInt(pos);
            this.hit.end = HitsInternalNoLock.this.ends.getInt(pos);
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

    protected final IntBigList docs;
    protected final IntBigList starts;
    protected final IntBigList ends;

    HitsInternalNoLock(long initialCapacity) {
        if (initialCapacity < 0) {
            // Use default initial capacities
            this.docs = new IntBigArrayBigList();
            this.starts = new IntBigArrayBigList();
            this.ends = new IntBigArrayBigList();
        } else {
            this.docs = new IntBigArrayBigList(initialCapacity);
            this.starts = new IntBigArrayBigList(initialCapacity);
            this.ends = new IntBigArrayBigList(initialCapacity);
        }
    }

    public void add(int doc, int start, int end) {
        docs.add(doc);
        starts.add(start);
        ends.add(end);
    }

    /**
     * Add the hit to the end of this list, copying the values. The hit object itself is not retained.
     */
    public void add(EphemeralHit hit) {
        docs.add(hit.doc);
        starts.add(hit.start);
        ends.add(hit.end);
    }

    /**
     * Add the hit to the end of this list, copying the values. The hit object itself is not retained.
     */
    public void add(Hit hit) {
        docs.add(hit.doc());
        starts.add(hit.start());
        ends.add(hit.end());
    }

    public void addAll(HitsInternalNoLock hits) {
        docs.addAll(hits.docs);
        starts.addAll(hits.starts);
        ends.addAll(hits.ends);
    }

    public void addAll(HitsInternalRead hits) {
        hits.withReadLock(hr -> {
            for (EphemeralHit h : hits) {
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

    public void withReadLock(Consumer<HitsInternalRead> cons) {
        cons.accept(this);
    }

    public Hit get(long index) {
        return new HitImpl(docs.getInt((int) index), starts.getInt((int) index), ends.getInt((int) index));
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
    public void getEphemeral(long index, EphemeralHit h) {
        h.doc = docs.getInt(index);
        h.start = starts.getInt(index);
        h.end = ends.getInt(index);
    }

    public int doc(long index) {
        return this.docs.getInt(index);
    }

    public int start(long index) {
        return this.starts.getInt(index);
    }

    public int end(long index) {
        return this.ends.getInt(index);
    }

    public long size() {
        return docs.size64();
    }

    /**
     * Expert use: get the internal docs array.
     * The array is not locked, so care should be taken when reading it.
     * Best to wrap usage of this function and the returned in a withReadLock call.
     * <p>
     * NOTE JN: only used in HitsList constructor; eliminate entirely?
     *
     * @return list of document ids
     */
    public IntIterator docsIterator() {
        return docs.intIterator();
    }

    /**
     * Note: iterating does not lock the arrays, to do that, it should be performed in a {@link #withReadLock} callback.
     */
    @Override
    public Iterator iterator() {
        return new HitIterator();
    }

    public HitsInternal sort(HitProperty p) {
        HitsInternal r;
        long size = docs.size64();
        if (size > Integer.MAX_VALUE) {
            // Fill an indices BigArray with 0 ... size
            long[][] indices = LongBigArrays.newBigArray(size);
            int i = 0;
            for (final long[] segment : indices) {
                for (int displacement = 0; displacement < segment.length; displacement++) {
                    segment[displacement] = i;
                    i++;
                }
            }

            // Sort the indices using the given HitProperty
            LongBigArrays.quickSort(indices, p);

            // Now use the sorted indices to fill a new HitsInternal with the actual hits
            r = HitsInternal.create(size, true, false);
            for (final long[] segment: indices) {
                for (long l: segment) {
                    r.add(docs.getInt(l), starts.getInt(l), ends.getInt(l));
                }
            }
        } else {
            // We can use regular arrays Collections classes, faster
            int[] indices = new int[(int) size];
            for (int i = 0; i < indices.length; ++i)
                indices[i] = i;

            IntArrays.quickSort(indices, p::compare);

            r = HitsInternal.create(size, false, false);
            for (int index : indices) {
                r.add(docs.getInt(index), starts.getInt(index), ends.getInt(index));
            }
        }
        return r;
    }
}
