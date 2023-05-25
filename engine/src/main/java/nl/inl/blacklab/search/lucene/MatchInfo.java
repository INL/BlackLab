package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.util.BytesRef;

public abstract class MatchInfo implements Comparable<MatchInfo> {

    /**
     * Null-safe equality test of two MatchInfos.
     *
     * Returns true if both are null, or if they are equal.
     *
     * @param a the first MatchInfo
     * @param b the second MatchInfo
     * @return true iff both are null, or if they are equal
     */
    public static boolean equal(MatchInfo[] a, MatchInfo[] b) {
        if ((a == null) != (b == null)) {
            // One is null, the other is not.
            return false;
        }
        if (a == null) {
            // Both null
            return true;
        }
        // Both set
        return a.equals(b);
    }

    public static void serializeInlineTag(int start, int end, DataOutput dataOutput) throws IOException {
        int relativePositionOfLastToken = end - start;
        dataOutput.writeZInt(relativePositionOfLastToken);
        // (rest of MatchInfo members have the default value so we skip them)
    }

    public abstract void deserialize(int currentTokenPosition, ByteArrayDataInput dataInput) throws IOException;

    public abstract void serialize(int currentTokenPosition, DataOutput dataOutput) throws IOException;

    public abstract BytesRef serialize(int currentTokenPosition);

    public abstract boolean isSpan();

    @Override
    public abstract String toString();

    @Override
    public int compareTo(MatchInfo o) {
        // Subclasses should compare properly if types match;
        // if not, just compare class names
        return getClass().getName().compareTo(o.getClass().getName());
    }

    @Override
    public abstract boolean equals(Object o);

    @Override
    public abstract int hashCode();

    public abstract int getSpanStart();

    public abstract int getSpanEnd();

    public boolean isSpanEmpty() {
        return getSpanStart() == getSpanEnd();
    }

    public boolean isTag() {
        return false;
    }
}
