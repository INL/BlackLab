package nl.inl.blacklab.search.lucene;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.OutputStreamDataOutput;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.search.indexmetadata.RelationUtil;

/**
 * Information about a relation's source and target,
 * and optionally the relation type.
 * <p>
 * Note that this is not named MatchInfoRelation, as it is
 * used while indexing as well as matching.
 */
public class RelationInfo extends MatchInfo {

    public static RelationInfo create() {
        return new RelationInfo(false, -1, -1, -1, -1, null, null, null);
    }

    public static RelationInfo createWithOverriddenField(String overriddenField) {
        return new RelationInfo(false, -1, -1, -1, -1, null, null, overriddenField);
    }

    public static RelationInfo create(boolean onlyHasTarget, int sourceStart, int sourceEnd, int targetStart, int targetEnd) {
        return new RelationInfo(onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd, null, null, null);
    }

    public static RelationInfo create(boolean onlyHasTarget, int sourceStart, int sourceEnd, int targetStart, int targetEnd, String fullRelationType) {
        return new RelationInfo(onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd, fullRelationType, null, null);
    }

    /** Include attributes in relation info? We wanted to do this but can't anymore
     *  because they're only available in the version indexed with attributes. We also index
     *  relations without attributes to speed up searches that don't filter on relations,
     *  and this would make responses inconsistent. The alternative is storing attribute info
     *  in the payload, but this will take more space and it's not clear if this is worth it. */
    public static final boolean INCLUDE_ATTRIBUTES_IN_RELATION_INFO = false;

    public static void serializeInlineTag(int start, int end, DataOutput dataOutput) throws IOException {
        int relativePositionOfLastToken = end - start;
        dataOutput.writeZInt(relativePositionOfLastToken);
        // (rest of RelationInfo members have the default value so we skip them)
    }

    public static void serializeRelation(boolean onlyHasTarget, int sourceStart, int sourceEnd,
            int targetStart, int targetEnd, DataOutput dataOutput) {

        assert sourceStart >= 0 && sourceEnd >= 0 && targetStart >= 0 && targetEnd >= 0;
        // Determine values to write from our source and target, and the position we're being indexed at
        int thisLength = sourceEnd - sourceStart;
        int relOtherStart = targetStart - sourceStart;
        int otherLength = targetEnd - targetStart;

        // Which default length should we use? (can save 1 byte per relation)
        boolean useAlternateDefaultLength = thisLength == DEFAULT_LENGTH_ALT && otherLength == DEFAULT_LENGTH_ALT;
        int defaultLength = useAlternateDefaultLength ? DEFAULT_LENGTH_ALT : DEFAULT_LENGTH;

        byte flags = (byte) ((onlyHasTarget ? FLAG_ONLY_HAS_TARGET : 0)
                | (useAlternateDefaultLength ? FLAG_DEFAULT_LENGTH_ALT : 0));

        // Only write as much as we need (omitting default values from the end)
        boolean writeOtherLength = otherLength != defaultLength;
        boolean writeThisLength = writeOtherLength || thisLength != defaultLength;
        boolean writeFlags = writeThisLength || flags != DEFAULT_FLAGS;
        boolean writeRelOtherStart = writeFlags || relOtherStart != DEFAULT_REL_OTHER_START;
        try {
            if (writeRelOtherStart)
                dataOutput.writeZInt(relOtherStart);
            if (writeFlags)
                dataOutput.writeByte(flags);
            if (writeThisLength)
                dataOutput.writeVInt(thisLength);
            if (writeOtherLength)
                dataOutput.writeVInt(otherLength);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Check that this relation has a target set.
     * <p>
     * E.g. when indexing a span ("inline tag"), we don't know the target until we encounter the closing tag,
     * so we can't store the payload until then.
     *
     * @return whether we have a target or not
     */
    public boolean hasTarget() {
        assert targetStart >= 0 && targetEnd >= 0 || targetStart < 0 && targetEnd < 0 : "targetStart and targetEnd inconsistent";
        return targetStart >= 0;
    }

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
     * <p>
     * See there for details.
     */
    private static final int DEFAULT_LENGTH_ALT = 1;

    /** Is it a root relationship, that only has a target, no source? */
    public static final byte FLAG_ONLY_HAS_TARGET = 0x02;

    /** If set, use DEFAULT_LENGTH_ALT (1) as the default length
     * (dependency relations) instead of 0 (tags).
     * <p>
     * Doing it this way saves us a byte in the payload for dependency relations, as
     * we don't have to store two 1s, just one flags value.
     */
    public static final byte FLAG_DEFAULT_LENGTH_ALT = 0x04;

    /** Does this relation only have a target? (i.e. a root relation) */
    private boolean onlyHasTarget;

    /** Where does the source of the relation start?
     *  NOTE: if the relation has no source, set this to the targetStart as a convention. Invalid if < 0! */
    private int sourceStart;

    /** Where does the source of the relation end?
     *  NOTE: if the relation has no source, set this to the targetEnd as a convention. Invalid if < 0! */
    private int sourceEnd;

    /** Where does the target of the relation start?
     (the target is called 'dep' in dependency relations) */
    private int targetStart;

    /** Where does the target of the relation end? */
    private int targetEnd;

    /** Our relation type, or null if not set (set during search by SpansRelations) */
    private String fullRelationType;

    /** Tag attributes (if any), or empty if not set (set during search by SpansRelations) */
    private Map<String, String> attributes;

    private RelationInfo(boolean onlyHasTarget, int sourceStart, int sourceEnd, int targetStart, int targetEnd,
            String fullRelationType, Map<String, String> attributes, String overriddenField) {
        super(overriddenField);
        this.fullRelationType = fullRelationType;
        this.attributes = attributes == null ? Collections.emptyMap() : attributes;
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

    public RelationInfo copy() {
        return new RelationInfo(onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd, fullRelationType,
                attributes, getOverriddenField());
    }

    /**
     * Deserialize relation info from the payload.
     *
     * @param currentTokenPosition the position we're currently at
     * @param dataInput data to deserialize
     * @throws IOException on corrupted payload
     */
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

        // Fill the relationinfo structure with the source and target start/end positions
        this.onlyHasTarget = (flags & FLAG_ONLY_HAS_TARGET) != 0;
        this.sourceStart = currentTokenPosition;
        this.sourceEnd = currentTokenPosition + thisLength;
        this.targetStart = currentTokenPosition + relOtherStart;
        this.targetEnd = this.targetStart + otherLength;
        assert sourceStart >= 0 && sourceEnd >= 0 && targetStart >= 0 && targetEnd >= 0;
    }

    /**
     * Serialize to a BytesRef.
     *
     * @return the serialized data
     */
    public BytesRef serialize() {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        DataOutput dataOutput = new OutputStreamDataOutput(os);
        serializeRelation(onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd, dataOutput);
        return new BytesRef(os.toByteArray());
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

    /**
     * Does this relation info represent an inline tag?
     * <p>
     * Inline tags are indexed as relations with zero-length source and target.
     * Unlike other relations, source always occurs before target for tag relations.
     * <p>
     * The reason this method exists is that the classic external index doesn't support
     * "regular" relations but does support inline tags. When we eventually drop support
     * for the classic external index format, this method can be removed.
     *
     * @return true if this relation info represents an inline tag
     */
    private boolean isTag() {
        // A tag is a relation with source and target, both of which are length 0, and source occurs before target.
        // (target can also be -1, which means we don't know yet)
        // (or rather, such a relation can be indexed as a tag in the classic external index)
        return !onlyHasTarget && (sourceEnd - sourceStart == 0 && targetEnd - targetStart == 0 &&
                (targetStart == -1 || sourceStart <= targetStart));
    }

    @Override
    public Type getType() {
        return isTag() ? Type.INLINE_TAG : Type.RELATION;
    }

    /**
     * Pass the indexed term for this relation, so we can decode it.
     * <p>
     * We decode the relation class and type and any attributes from the indexed term.
     * Note that if multiple values were indexed for a single attribute, only the first
     * value is extracted.
     *
     * @param term indexed term
     */
    public void setIndexedTerm(String term) {
        this.fullRelationType = RelationUtil.fullTypeFromIndexedTerm(term);
        if (INCLUDE_ATTRIBUTES_IN_RELATION_INFO)
            this.attributes = RelationUtil.attributesFromIndexedTerm(term);
    }

    /**
     * Get the full relation type, consisting of the class and type.
     *
     * @return full relation type
     */
    public String getFullRelationType() {
        return fullRelationType;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    @Override
    public String toString() {
        // Inline tag
        if (isTag()) {
            String tagName = fullRelationType == null ? "UNKNOWN" : RelationUtil.classAndType(fullRelationType)[1];
            String attr = attributes == null || attributes.isEmpty() ? "" :
                    " " + attributes.entrySet().stream()
                            .map(e -> e.getKey() + "=\"" + e.getValue() + "\"")
                            .collect(Collectors.joining(" "));
            return "tag(<" + tagName + attr + "/> at " + getSpanStart() + "-" + getSpanEnd() + " )" + toStringOptFieldName();
        }

        // Relation
        int targetLen = targetEnd - targetStart;
        String target = targetStart + (targetLen != 1 ? "-" + targetEnd : "");
        if (isRoot())
            return "rel( ^-" + fullRelationType + "-> " + target + ")";
        int sourceLen = sourceEnd - sourceStart;
        String source = sourceStart + (sourceLen != 1 ? "-" + sourceEnd : "");
        return "rel(" + source + " -" + fullRelationType + "-> " + target + ")" + toStringOptFieldName();
    }

    @Override
    public int compareTo(MatchInfo o) {
        if (o instanceof RelationInfo)
            return compareTo((RelationInfo) o);
        return super.compareTo(o);
    }

    public int compareTo(RelationInfo o) {
        int n;
        n = -Boolean.compare(onlyHasTarget, o.onlyHasTarget);
        if (n != 0)
            return n;
        if (!onlyHasTarget && !o.onlyHasTarget) {
            n = Integer.compare(sourceStart, o.sourceStart);
            if (n != 0)
                return n;
            n = Integer.compare(sourceEnd, o.sourceEnd);
            if (n != 0)
                return n;
        }
        n = Integer.compare(targetStart, o.targetStart);
        if (n != 0)
            return n;
        n = Integer.compare(targetEnd, o.targetEnd);
        if (n != 0)
            return n;
        n = fullRelationType.compareTo(o.fullRelationType);
        if (n != 0)
            return n;
        if (attributes == null) {
            if (o.attributes != null)
                return -1;
            return 0;
        } else {
            if (o.attributes == null)
                return 1;
            n = Integer.compare(attributes.hashCode(), o.attributes.hashCode());
        }
        return n;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        RelationInfo relationInfo = (RelationInfo) o;
        return onlyHasTarget == relationInfo.onlyHasTarget &&
                sourceStart == relationInfo.sourceStart && sourceEnd == relationInfo.sourceEnd &&
                targetStart == relationInfo.targetStart && targetEnd == relationInfo.targetEnd &&
                Objects.equals(fullRelationType,relationInfo.fullRelationType) &&
                Objects.equals(attributes, relationInfo.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(onlyHasTarget, sourceStart, sourceEnd, targetStart, targetEnd, fullRelationType, attributes);
    }
}
