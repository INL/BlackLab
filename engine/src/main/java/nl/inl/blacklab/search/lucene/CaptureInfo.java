package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.util.BytesRef;

/**
 * Position information about a relation's source and target
 */
public class CaptureInfo extends MatchInfo {

    int start;

    int end;

    public CaptureInfo(int start, int end) {
        this.start = start;
        this.end = end;
    }

    public void deserialize(int currentTokenPosition, ByteArrayDataInput dataInput) throws IOException {
        throw new UnsupportedOperationException("Cannot (de)serialize span");
    }

    /**
     * Serialize to a DataOutput.
     *
     * @param currentTokenPosition the position of the token we're being indexed at
     * @param dataOutput the DataOutput to write to
     */
    public void serialize(int currentTokenPosition, DataOutput dataOutput) throws IOException {
        throw new UnsupportedOperationException("Cannot (de)serialize span");
    }

    /**
     * Serialize to a BytesRef.
     *
     * @param currentTokenPosition the position of the token we're being indexed at
     * @return the serialized data
     */
    public BytesRef serialize(int currentTokenPosition) {
        throw new UnsupportedOperationException("Cannot (de)serialize span");
    }

    public int getFullSpanStart() {
        return start;
    }

    public int getFullSpanEnd() {
        return end;
    }

    @Override
    public boolean isSpan() {
        return true;
    }

    @Override
    public String toString() {
        return "span(" + getFullSpanStart() + "-" + getFullSpanEnd() + ")";
    }

    @Override
    public int compareTo(MatchInfo o) {
        if (o instanceof CaptureInfo)
            return compareTo((CaptureInfo) o);
        return super.compareTo(o);
    }

    public int compareTo(CaptureInfo o) {
        int n;
        n = Integer.compare(start, o.start);
        if (n != 0)
            return n;
        n = Integer.compare(end, o.end);
        return n;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CaptureInfo captureInfo = (CaptureInfo) o;
        return start == captureInfo.start && end == captureInfo.end;
    }

    @Override
    public int hashCode() {
        return Objects.hash(start, end);
    }
}
