package nl.inl.blacklab.search.lucene;

import java.io.IOException;
import java.util.Objects;

import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.DataOutput;

/**
 * Position information about a relation's source and target
 */
public class RelationInfo {

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

    /** Was the relationship indexed at the target instead of the source? */
    public static final byte FLAG_INDEXED_AT_TARGET = 0x01;

    /** Is it a root relationship, that only has a target, no source? */
    public static final byte FLAG_ONLY_HAS_TARGET = 0x02;

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

    public RelationInfo() {
        this(false, -1, -1, -1, -1);
    }

    public RelationInfo(boolean onlyHasTarget, int sourceStart, int sourceEnd, int targetStart, int targetEnd) {
        this.onlyHasTarget = onlyHasTarget;
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

    public static void serializeInlineTag(int start, int end, DataOutput dataOutput) throws IOException {
        int relativePositionOfLastToken = end - start;
        dataOutput.writeZInt(relativePositionOfLastToken);
        // (rest of RelationInfo members have the default value so we skip them)
    }

    public void serialize(int currentTokenPosition, DataOutput dataOutput) throws IOException {
        // Determine values to write from our source and target, and the position we're being indexed at
        boolean indexedAtTarget = targetStart == currentTokenPosition;
        byte flags = (byte) ((onlyHasTarget ? FLAG_ONLY_HAS_TARGET : 0)
                        | (indexedAtTarget ? FLAG_INDEXED_AT_TARGET : 0));
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

        // Only write as much as we need (omitting default values from the end)
        boolean writeOtherLength = otherLength != DEFAULT_LENGTH;
        boolean writeThisLength = writeOtherLength || thisLength != DEFAULT_LENGTH;
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

    public void copyFrom(RelationInfo other) {
        onlyHasTarget = other.onlyHasTarget;
        sourceStart = other.sourceStart;
        sourceEnd = other.sourceEnd;
        targetStart = other.targetStart;
        targetEnd = other.targetEnd;
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

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof RelationInfo))
            return false;
        RelationInfo that = (RelationInfo) o;
        return onlyHasTarget == that.onlyHasTarget && sourceStart == that.sourceStart && sourceEnd == that.sourceEnd
                && targetStart == that.targetStart && targetEnd == that.targetEnd;
    }

    @Override
    public int hashCode() {
        return Objects.hash(onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd);
    }

    public boolean isTag() {
        // A tag is a relation with source and target, both of which are length 0, and source occurs before target.
        // (target can also be -1, which means we don't know yet)
        // (or rather, such a relation can be indexed as a tag in the classic external index)
        return !onlyHasTarget && (sourceEnd - sourceStart == 0 && targetEnd - targetStart == 0 &&
                (targetStart == -1 || sourceStart <= targetStart));
    }
}
