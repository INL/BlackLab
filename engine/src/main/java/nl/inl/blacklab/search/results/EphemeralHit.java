package nl.inl.blacklab.search.results;

/**
 * A mutable implementation of Hit, to be used for short-lived
 * instances used while e.g. iterating through a list of hits.
 */
class EphemeralHit implements Hit {
    int doc = -1;
    int start = -1;
    int end = -1;

    Hit toHit() {
        return new HitImpl(doc, start, end);
    }

    @Override
    public int doc() {
        return doc;
    }

    @Override
    public int start() {
        return start;
    }

    @Override
    public int end() {
        return end;
    }
}
