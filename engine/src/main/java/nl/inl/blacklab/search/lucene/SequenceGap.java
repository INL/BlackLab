package nl.inl.blacklab.search.lucene;

/**
 * Allowable gap size between parts of a sequence.
 */
public class SequenceGap {

    public static final SequenceGap NONE = fixed(0);

    public static final SequenceGap ANY = atLeast(0);

    public static SequenceGap atLeast(int minSize) {
        return new SequenceGap(minSize, BLSpans.MAX_UNLIMITED);
    }

    public static SequenceGap atMost(int maxSize) {
        return new SequenceGap(0, maxSize);
    }

    public static SequenceGap fixed(int size) {
        return new SequenceGap(size, size);
    }

    public static SequenceGap variable(int minSize, int maxSize) {
        return new SequenceGap(minSize, maxSize);
    }

    private final int minSize;

    private final int maxSize;

    public SequenceGap(int minSize, int maxSize) {
        super();
        this.minSize = minSize;
        this.maxSize = maxSize;
    }

    public int minSize() {
        return minSize;
    }

    public int maxSize() {
        return maxSize;
    }

    public boolean isFixed() {
        return minSize == maxSize;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + maxSize;
        result = prime * result + minSize;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        SequenceGap other = (SequenceGap) obj;
        if (maxSize != other.maxSize)
            return false;
        if (minSize != other.minSize)
            return false;
        return true;
    }

    @Override
    public String toString() {
        return minSize + "-" + maxSize;
    }

}
