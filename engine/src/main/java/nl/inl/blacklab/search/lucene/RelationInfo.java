package nl.inl.blacklab.search.lucene;

import java.io.IOException;

import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.DataOutput;

/**
 * Position information about a relation's source and target
 */
public class RelationInfo {

    private static final int DEFAULT_REL_OTHER_START = 1;

    private static final byte DEFAULT_FLAGS = 0;

    private static final int DEFAULT_LENGTH = 1;

    private boolean onlyHasTarget;

    private int sourceStart;

    private int sourceEnd;

    private int targetStart;

    private int targetEnd;

    public RelationInfo() {
        onlyHasTarget = false;
        sourceStart = -1;
        sourceEnd = -1;
        targetStart = -1;
        targetEnd = -1;
    }

    public void deserialize(int currentTokenPosition, ByteArrayDataInput dataInput) throws IOException {
        // Read values from payload (or use defaults for missing values)
        int relOtherStart = DEFAULT_REL_OTHER_START, thisLength = DEFAULT_LENGTH, otherLength = DEFAULT_LENGTH;
        byte flags = DEFAULT_FLAGS;
        if (!dataInput.eof()) {
            relOtherStart = dataInput.readVInt();
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
        this.onlyHasTarget = (flags & SpansTagsIntegrated.REL_FLAG_ONLY_HAS_TARGET) != 0;
        if ((flags & SpansTagsIntegrated.REL_FLAG_INDEXED_AT_TARGET) != 0) {
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

    public void serialize(int currentTokenPosition, DataOutput dataOutput) throws IOException {
        // Determine values to write from our source and target, and the position we're being indexed at
        boolean indexedAtTarget = targetStart == currentTokenPosition;
        byte flags = (byte) ((onlyHasTarget ? SpansTagsIntegrated.REL_FLAG_ONLY_HAS_TARGET : 0)
                        | (indexedAtTarget ? SpansTagsIntegrated.REL_FLAG_INDEXED_AT_TARGET : 0));
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
            dataOutput.writeVInt(relOtherStart);
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

    public boolean isOnlyHasTarget() {
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
}
