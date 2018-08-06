package nl.inl.blacklab.search.indexmetadata;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.index.complex.AnnotationWriter.SensitivitySetting;

/** Annotation on a field. */
class AnnotationImpl implements Annotation, Freezable {
    
    /** The field this is an annotation for. */
    private AnnotatedField field;
    
    /** The property name */
    private String name;

    /** Name to display in user interface (optional) */
    private String displayName;

    /** Description for user interface (optional) */
    private String description = "";

    /**
     * What UI element to use for this property (e.g. text, select); only used in
     * frontend, ignored by BlackLab itself.
     */
    private String uiType = "";

    /** Reference to match sensitivities this annotation has */
    private Set<AnnotationSensitivity> alternatives = new HashSet<>();

    /** Match sensitivity values this annotation has */
    private Set<MatchSensitivity> matchSensitivities = new HashSet<>();
    
    /**
     * What sensitivity alternatives (sensitive/insensitive for case & diacritics)
     * are present
     */
    private SensitivitySetting sensitivitySetting = SensitivitySetting.ONLY_SENSITIVE;

    /** Whether or not this annotation has a forward index. */
    private boolean forwardIndex;

    /**
     * Which of the alternatives is the main one (containing the offset info, if
     * present)
     */
    private AnnotationSensitivity offsetsAlternative;

    private boolean frozen;

    AnnotationImpl(AnnotatedField field) {
        this(field, null);
    }

    AnnotationImpl(AnnotatedField field, String name) {
        this.field = field;
        this.name = name;
        forwardIndex = false;
    }
    
    @Override
    public AnnotatedField field() {
        return field;
    }

    @Override
    public String toString() {
        String sensitivityDesc;
        switch (sensitivitySetting) {
        case ONLY_SENSITIVE:
            sensitivityDesc = "sensitive only";
            break;
        case ONLY_INSENSITIVE:
            sensitivityDesc = "insensitive only";
            break;
        case SENSITIVE_AND_INSENSITIVE:
            sensitivityDesc = "sensitive and insensitive";
            break;
        case CASE_AND_DIACRITICS_SEPARATE:
            sensitivityDesc = "case/diacritics sensitivity separate";
            break;
        default:
            throw new IllegalArgumentException("Unknown sensitivity " + sensitivitySetting.toString());
        }
        return (name.length() == 0 ? "(default)" : name)
                + (forwardIndex ? " (+FI)" : "") + ", " + sensitivityDesc;
    }

    @Override
    public boolean hasForwardIndex() {
        return forwardIndex;
    }

    /**
     * Get this property's name
     * 
     * @return the name
     */
    @Override
    public String name() {
        return name;
    }

    @Override
    public String sensitivitySettingDesc() {
        return sensitivitySetting.toString();
    }

    @Override
    public Collection<AnnotationSensitivity> sensitivities() {
        return Collections.unmodifiableSet(alternatives);
    }
    
    @Override
    public boolean hasSensitivity(MatchSensitivity sensitivity) {
        return matchSensitivities.contains(sensitivity);
    }

    @Override
    public AnnotationSensitivity sensitivity(MatchSensitivity sensitivity) {
        if (!hasSensitivity(sensitivity))
            throw new UnsupportedOperationException("Specified sensitivity not present for field " + luceneFieldPrefix());
        return new AnnotationSensitivity() {
            @Override
            public Annotation annotation() {
                return AnnotationImpl.this;
            }

            @Override
            public MatchSensitivity sensitivity() {
                return sensitivity;
            }
        };
    }

    /**
     * Return which alternative contains character offset information.
     *
     * Note that there may not be such an alternative.
     *
     * @return the alternative, or null if there is none.
     */
    @Override
    public AnnotationSensitivity offsetsSensitivity() {
        return offsetsAlternative;
    }

    @Override
    public String displayName() {
        if (displayName != null)
            return displayName;
        if (name.equals("pos"))
            return "PoS";
        return StringUtils.capitalize(name);
    }

    @Override
    public boolean isInternal() {
        return name.equals(AnnotatedFieldNameUtil.START_TAG_PROP_NAME) ||
                name.equals(AnnotatedFieldNameUtil.END_TAG_PROP_NAME) ||
                name.equals(AnnotatedFieldNameUtil.PUNCTUATION_PROP_NAME);
    }

    @Override
    public String uiType() {
        return uiType;
    }

    @Override
    public String description() {
        return description;
    }
    
    // Methods that mutate data
    // -----------------------------------------------------

    /**
     * Detect which alternative is the one containing character offsets.
     *
     * Note that there may not be such an alternative.
     *
     * @param reader the index reader
     * @param fieldName the field this property belongs under
     * @return true if found, false if not
     */
    public boolean detectOffsetsAlternative(IndexReader reader, String fieldName) {
        ensureNotFrozen();
        // Iterate over the alternatives and for each alternative, find a term
        // vector. If that has character offsets stored, it's our main property.
        // If not, keep searching.
        for (AnnotationSensitivity sensitivity: alternatives) {
            if (IndexMetadataImpl.hasOffsets(reader, sensitivity.luceneField())) {
                offsetsAlternative = sensitivity;
                return true;
            }
        }
    
        return false;
    }

    public void setDisplayName(String displayName) {
        ensureNotFrozen();
        this.displayName = displayName;
    }

    void addAlternative(MatchSensitivity matchSensitivity) {
        ensureNotFrozen();
        alternatives.add(new AnnotationSensitivityImpl(this, matchSensitivity));
        matchSensitivities.add(matchSensitivity);
    
        // Update the sensitivity settings based on the alternatives we've seen so far.
        if (matchSensitivities.contains(MatchSensitivity.SENSITIVE)) {
            if (matchSensitivities.contains(MatchSensitivity.INSENSITIVE)) {
                if (matchSensitivities.contains(MatchSensitivity.CASE_INSENSITIVE)) {
                    sensitivitySetting = SensitivitySetting.CASE_AND_DIACRITICS_SEPARATE;
                } else {
                    sensitivitySetting = SensitivitySetting.SENSITIVE_AND_INSENSITIVE;
                }
            } else {
                sensitivitySetting = SensitivitySetting.ONLY_SENSITIVE;
            }
        } else {
            sensitivitySetting = SensitivitySetting.ONLY_INSENSITIVE;
        }
    }

    void setForwardIndex(boolean b) {
        ensureNotFrozen();
        forwardIndex = b;
    }

    public void setName(String propName) {
        ensureNotFrozen();
        this.name = propName;
    }

    public void setUiType(String uiType) {
        ensureNotFrozen();
        this.uiType = uiType;
    }

    public void setDescription(String description) {
        ensureNotFrozen();
        this.description = description;
    }
    
    @Override
    public void freeze() {
        this.frozen = true;
    }
    
    @Override
    public boolean isFrozen() {
        return this.frozen;
    }

}
