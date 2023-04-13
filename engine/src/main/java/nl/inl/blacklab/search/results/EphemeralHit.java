package nl.inl.blacklab.search.results;

import java.util.Arrays;
import java.util.Objects;

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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        EphemeralHit that = (EphemeralHit) o;
        return doc == that.doc && start == that.start && end == that.end && Arrays.equals(matchInfo,
                that.matchInfo);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(doc, start, end);
        result = 31 * result + Arrays.hashCode(matchInfo);
        return result;
    }
}
