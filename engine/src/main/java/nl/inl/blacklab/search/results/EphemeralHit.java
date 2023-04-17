package nl.inl.blacklab.search.results;

import nl.inl.blacklab.search.lucene.MatchInfo;

/**
 * A mutable implementation of Hit, to be used for short-lived
 * instances used while e.g. iterating through a list of hits.
 */
public class EphemeralHit implements Hit {
    public int doc = -1;
    public int start = -1;
    public int end = -1;
    public MatchInfo[] matchInfo = null;

    Hit toHit() {
        return new HitImpl(doc, start, end, matchInfo);
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

    @Override
    public MatchInfo[] matchInfo() { return matchInfo; }

}
