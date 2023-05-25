package nl.inl.blacklab.search.lucene;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.OutputStreamDataOutput;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.search.indexmetadata.RelationUtil;

/**
 * Position information about a relation's source and target
 */
public class RelationInfo extends MatchInfo {

    /**
     * Different spans we can return for a relation
     */
    public enum SpanMode {
        // Return the source span
        SOURCE("source"),

        // Return the target span
        TARGET("target"),

        // Return a span covering both source and target
        FULL_SPAN("full"),

        // Return a span covering source and target of all matched relations
        // (only valid for rspan(), not rel())
        ALL_SPANS("all");

        private final String code;

        SpanMode(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

        @Override
        public String toString() {
            return getCode();
        }

        public static SpanMode fromCode(String code) {
            for (SpanMode mode: values()) {
                if (mode.getCode().equals(code)) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Unknown span mode: " + code);
        }
    }

    /** Default value for where the other end of this relation starts.
     *  We use 1 because it's pretty common for adjacent words to have a
     *  relation, and in this case we don't store the value. */
    private static final int DEFAULT_REL_OTHER_START = 1;

    /**
     * Default value for the flags byte.
     * This means the relation was indexed at the source, and has both a source and target.
     * This is the most common case (e.g. always true for inline tags), so we use it as the default.
     */
    private static final byte DEFAULT_FLAGS = 0;

    /**
     * Default length for the source and target.
     * Inline tags are always stored with a 0-length source and target.
     * Dependency relations will usually store 1 (a single word), unless
     * the relation involves word group(s).
     */
    private static final int DEFAULT_LENGTH = 0;

    /**
     * Default length for the source and target if {@link #FLAG_DEFAULT_LENGTH_ALT} is set.
     *
     * See there for details.
     */
    private static final int DEFAULT_LENGTH_ALT = 1;

    /** Was the relationship indexed at the target instead of the source? */
    public static final byte FLAG_INDEXED_AT_TARGET = 0x01;

    /** Is it a root relationship, that only has a target, no source? */
    public static final byte FLAG_ONLY_HAS_TARGET = 0x02;

    /** If set, use DEFAULT_LENGTH_ALT (1) as the default length
     * (dependency relations) instead of 0 (tags).
     *
     * Doing it this way saves us a byte in the payload for dependency relations, as
     * we don't have to store two 1s, just one flags value.
     */
    public static final byte FLAG_DEFAULT_LENGTH_ALT = 0x04;

    /** Does this relation only have a target? (i.e. a root relation) */
    private boolean onlyHasTarget;

    /** Where does the source of the relation start?
        (the source is called 'head' in dependency relations) */
    private int sourceStart;

    /** Where does the source of the relation end? */
    private int sourceEnd;

    /** Where does the target of the relation start?
     (the target is called 'dep' in dependency relations) */
    private int targetStart;

    /** Where does the target of the relation end? */
    private int targetEnd;

    /** Our relation type, or null if not applicable or not set. */
    private String fullRelationType;

    public RelationInfo() {
        this(null, false, -1, -1, -1, -1);
    }

    public RelationInfo(String fullRelationType, boolean onlyHasTarget, int sourceStart, int sourceEnd, int targetStart, int targetEnd) {
        this.fullRelationType = fullRelationType;
        this.onlyHasTarget = onlyHasTarget;
        if (onlyHasTarget && (sourceStart != targetStart || sourceEnd != targetEnd)) {
            throw new IllegalArgumentException("By convention, root relations should have a 'fake source' that coincides with their target " +
                    "(values here are SRC " + sourceStart + ", " + sourceEnd + " - TGT " + targetStart + ", " + targetEnd + ").");
        }
        this.sourceStart = sourceStart;
        this.sourceEnd = sourceEnd;
        this.targetStart = targetStart;
        this.targetEnd = targetEnd;
    }

    public void deserialize(int currentTokenPosition, ByteArrayDataInput dataInput) throws IOException {
        // Read values from payload (or use defaults for missing values)
        int relOtherStart = DEFAULT_REL_OTHER_START, thisLength = DEFAULT_LENGTH, otherLength = DEFAULT_LENGTH;
        byte flags = DEFAULT_FLAGS;
        if (!dataInput.eof()) {
            relOtherStart = dataInput.readZInt();
            if (!dataInput.eof()) {
                flags = dataInput.readByte();
                if ((flags & FLAG_DEFAULT_LENGTH_ALT) != 0) {
                    // Use alternate default length
                    thisLength = DEFAULT_LENGTH_ALT;
                    otherLength = DEFAULT_LENGTH_ALT;
                }
                if (!dataInput.eof()) {
                    thisLength = dataInput.readVInt();
                    if (!dataInput.eof()) {
                        otherLength = dataInput.readVInt();
                    }
                }
            }
        }

        // Calculate start and end positions
        int thisEnd = currentTokenPosition + thisLength;
        int otherStart = currentTokenPosition + relOtherStart;
        int otherEnd = otherStart + otherLength;

        // Fill the relationinfo structure with the source and target start/end positions
        this.onlyHasTarget = (flags & FLAG_ONLY_HAS_TARGET) != 0;
        if ((flags & FLAG_INDEXED_AT_TARGET) != 0) {
            // Relation was indexed at the target position.
            this.sourceStart = otherStart;
            this.sourceEnd = otherEnd;
            this.targetStart = currentTokenPosition;
            this.targetEnd = thisEnd;
        } else {
            // Relation was indexed at the source position.
            this.sourceStart = currentTokenPosition;
            this.sourceEnd = thisEnd;
            this.targetStart = otherStart;
            this.targetEnd = otherEnd;
        }
    }

    /**
     * Serialize to a DataOutput.
     *
     * @param currentTokenPosition the position of the token we're being indexed at
     * @param dataOutput the DataOutput to write to
     */
    public void serialize(int currentTokenPosition, DataOutput dataOutput) throws IOException {
        // Determine values to write from our source and target, and the position we're being indexed at
        boolean indexedAtTarget = targetStart == currentTokenPosition;
        int relOtherStart, thisLength, otherLength;
        if (indexedAtTarget) {
            // this == target, other == source
            thisLength = targetEnd - targetStart;
            relOtherStart = sourceStart - currentTokenPosition;
            otherLength = sourceEnd - sourceStart;
        } else {
            // this == source, other == target
            thisLength = sourceEnd - sourceStart;
            relOtherStart = targetStart - currentTokenPosition;
            otherLength = targetEnd - targetStart;
        }

        // Which default length should we use? (can save 1 byte per relation)
        boolean useAlternateDefaultLength = thisLength == DEFAULT_LENGTH_ALT && otherLength == DEFAULT_LENGTH_ALT;
        int defaultLength = useAlternateDefaultLength ? DEFAULT_LENGTH_ALT : DEFAULT_LENGTH;

        byte flags = (byte) ((onlyHasTarget ? FLAG_ONLY_HAS_TARGET : 0)
                | (indexedAtTarget ? FLAG_INDEXED_AT_TARGET : 0)
                | (useAlternateDefaultLength ? FLAG_DEFAULT_LENGTH_ALT : 0));

        // Only write as much as we need (omitting default values from the end)
        boolean writeOtherLength = otherLength != defaultLength;
        boolean writeThisLength = writeOtherLength || thisLength != defaultLength;
        boolean writeFlags = writeThisLength || flags != DEFAULT_FLAGS;
        boolean writeRelOtherStart = writeFlags || relOtherStart != DEFAULT_REL_OTHER_START;
        if (writeRelOtherStart)
            dataOutput.writeZInt(relOtherStart);
        if (writeFlags)
            dataOutput.writeByte(flags);
        if (writeThisLength)
            dataOutput.writeVInt(thisLength);
        if (writeOtherLength)
            dataOutput.writeVInt(otherLength);
    }

    /**
     * Serialize to a BytesRef.
     *
     * @param currentTokenPosition the position of the token we're being indexed at
     * @return the serialized data
     */
    public BytesRef serialize(int currentTokenPosition) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            serialize(currentTokenPosition, new OutputStreamDataOutput(os));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new BytesRef(os.toByteArray());
    }

    public RelationInfo copy() {
        return new RelationInfo(fullRelationType, onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd);
    }

    public boolean isRoot() {
        return onlyHasTarget;
    }

    public int getSourceStart() {
        return sourceStart;
    }

    public int getSourceEnd() {
        return sourceEnd;
    }

    public int getTargetStart() {
        return targetStart;
    }

    public int getTargetEnd() {
        return targetEnd;
    }

    public int getSpanStart() {
        return Math.min(sourceStart, targetStart);
    }

    public int getSpanEnd() {
        return Math.max(sourceEnd, targetEnd);
    }

    public int spanStart(SpanMode mode) {
        switch (mode) {
        case SOURCE:
            return getSourceStart();
        case TARGET:
            return getTargetStart();
        case FULL_SPAN:
            return getSpanStart();
        case ALL_SPANS:
            throw new IllegalArgumentException("ALL_SPANS should have been handled elsewhere");
        default:
            throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    public int spanEnd(SpanMode mode) {
        switch (mode) {
        case SOURCE:
            return getSourceEnd();
        case TARGET:
            return getTargetEnd();
        case FULL_SPAN:
            return getSpanEnd();
        case ALL_SPANS:
            throw new IllegalArgumentException("ALL_SPANS should have been handled elsewhere");
        default:
            throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }

    public boolean isTag() {
        // A tag is a relation with source and target, both of which are length 0, and source occurs before target.
        // (target can also be -1, which means we don't know yet)
        // (or rather, such a relation can be indexed as a tag in the classic external index)
        return !onlyHasTarget && (sourceEnd - sourceStart == 0 && targetEnd - targetStart == 0 &&
                (targetStart == -1 || sourceStart <= targetStart));
    }

    @Override
    public boolean isSpan() {
        return false;
    }

    /**
     * Pass the indexed term for this relation, so we can decode it.
     *
     * @param term indexed term
     */
    public void setRelationTerm(String term) {
        this.fullRelationType = RelationUtil.fullTypeFromIndexedTerm(term);
    }

    /**
     * Get the full relation type, consisting of the class and type.
     *
     * @return full relation type
     */
    public String getFullRelationType() {
        return fullRelationType;
    }

    @Override
    public String toString() {
        if (isRoot())
            return "rootrel(" + fullRelationType + ", " + targetStart + "-" + targetEnd + ")";
        if (isSpan())
            return "span(" + getSpanStart() + "-" + getSpanEnd() + ")";
        if (isTag())
            return "tag(" + fullRelationType + ", " + getSpanStart() + "-" + getSpanEnd() + ")";
        return "rel(" + fullRelationType +
                ", sourceStart=" + sourceStart +
                ", sourceEnd=" + sourceEnd +
                ", targetStart=" + targetStart +
                ", targetEnd=" + targetEnd + ")";
    }

    @Override
    public int compareTo(MatchInfo o) {
        if (o instanceof RelationInfo)
            return compareTo((RelationInfo) o);
        return super.compareTo(o);
    }

    public int compareTo(RelationInfo o) {
        int n;
        n = Integer.compare(sourceStart, o.sourceStart);
        if (n != 0)
            return n;
        n = Integer.compare(sourceEnd, o.sourceEnd);
        if (n != 0)
            return n;
        n = Integer.compare(targetStart, o.targetEnd);
        if (n != 0)
            return n;
        n = Integer.compare(targetStart, o.targetEnd);
        if (n != 0)
            return n;
        n = Boolean.compare(onlyHasTarget, o.onlyHasTarget);
        if (n != 0)
            return n;
        n = Boolean.compare(onlyHasTarget, o.onlyHasTarget);
        if (n != 0)
            return n;
        n = fullRelationType.compareTo(o.fullRelationType);
        return n;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RelationInfo matchInfo = (RelationInfo) o;
        return onlyHasTarget == matchInfo.onlyHasTarget
                && sourceStart == matchInfo.sourceStart
                && sourceEnd == matchInfo.sourceEnd && targetStart == matchInfo.targetStart
                && targetEnd == matchInfo.targetEnd && Objects.equals(fullRelationType,
                matchInfo.fullRelationType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd, fullRelationType);
    }
}
