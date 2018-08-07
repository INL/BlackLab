package nl.inl.blacklab.search.results;

/**
 * Interface for a hit. Normally, hits are iterated over in a Lucene Spans object,
 * but in some places, it makes sense to place hits in separate objects: when
 * caching or sorting hits, or just for convenience in client code.
 */
public interface Hit extends Comparable<Hit> {
    
    static Hit create(int doc, int start, int end) {
        return HitStored.create(doc, start, end);
    }
    
    boolean isImmutable();

    /**
     * Returns an immutable copy of this hit.
     * 
     * If you want to store a hit, you must call this method first
     * to acquire an immutable copy of it.
     * 
     * The reason is that all Hit objects supplied by BlackLab should be 
     * assumed to be ephemeral (see above).
     * 
     * (already-immutable Hit instances will not create a copy, but simply 
     *  return themselves)
     * 
     * @return an immutable copy of this hit
     */
    default Hit save() {
        if (isImmutable())
            return this;
        return HitStored.create(doc(), start(), end());
    }
    
    boolean equals(Object with);

    default int compareTo(Hit o) {
        if (this == o)
            return 0;
        if (doc() == o.doc()) {
            if (start() == o.start()) {
                return end() - o.end();
            }
            return start() - o.start();
        }
        return doc() - o.doc();
    }

    String toString();

    int hashCode();

    int doc();

    int end();

    int start();

}
