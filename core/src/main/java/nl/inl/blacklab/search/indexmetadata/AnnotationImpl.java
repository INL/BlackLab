package nl.inl.blacklab.search.indexmetadata;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.indexmetadata.nint.Annotation;
import nl.inl.blacklab.search.indexmetadata.nint.AnnotationSensitivity;
import nl.inl.blacklab.search.indexmetadata.nint.Freezable;
import nl.inl.blacklab.search.indexmetadata.nint.MatchSensitivity;

/** Annotation on a field. */
class AnnotationImpl implements Annotation, Freezable {
    
    /** The field this is an annotation for. */
    private ComplexFieldDesc field;
    
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

    /** Any alternatives this property may have */
    private Set<MatchSensitivity> alternatives = new HashSet<>();

    /**
     * What sensitivity alternatives (sensitive/insensitive for case & diacritics)
     * are present
     */
    private SensitivitySetting sensitivity = SensitivitySetting.ONLY_SENSITIVE;

    /** Whether or not this annotation has a forward index. */
    private boolean forwardIndex;

    /**
     * Which of the alternatives is the main one (containing the offset info, if
     * present)
     */
    private MatchSensitivity offsetsAlternative;

    private boolean frozen;

    AnnotationImpl(ComplexFieldDesc field) {
        this(field, null);
    }

    AnnotationImpl(ComplexFieldDesc field, String name) {
        this.field = field;
        this.name = name;
        forwardIndex = false;
    }
    
    public ComplexFieldDesc field() {
        return field;
    }

    @Override
    public String toString() {
        String sensitivityDesc;
        switch (sensitivity) {
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
            throw new IllegalArgumentException("Unknown sensitivity " + sensitivity.toString());
        }
        return (name.length() == 0 ? "(default)" : name)
                + (forwardIndex ? " (+FI)" : "") + ", " + sensitivityDesc;
    }

    public boolean hasForwardIndex() {
        return forwardIndex;
    }

    /**
     * Get this property's name
     * 
     * @return the name
     */
    public String name() {
        return name;
    }

    @Override
    public String sensitivitySettingDesc() {
        return sensitivity.toString();
    }

    public Collection<MatchSensitivity> sensitivities() {
        return Collections.unmodifiableSet(alternatives);
    }
    
    public boolean hasSensitivity(MatchSensitivity sensitivity) {
        return alternatives.contains(sensitivity);
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
    public AnnotationSensitivity offsetsSensitivity() {
        return offsetsAlternative == null ? null : new AnnotationSensitivity() {
            @Override
            public Annotation annotation() {
                return AnnotationImpl.this;
            }

            @Override
            public MatchSensitivity sensitivity() {
                return offsetsAlternative;
            }
        };
    }

    public String displayName() {
        if (displayName != null)
            return displayName;
        if (name.equals("pos"))
            return "PoS";
        return StringUtils.capitalize(name);
    }

    public boolean isInternal() {
        return name.equals(ComplexFieldUtil.START_TAG_PROP_NAME) ||
                name.equals(ComplexFieldUtil.END_TAG_PROP_NAME) ||
                name.equals(ComplexFieldUtil.PUNCTUATION_PROP_NAME);
    }

    public String uiType() {
        return uiType;
    }

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
        for (MatchSensitivity sensitivity: alternatives) {
            String luceneAltName = ComplexFieldUtil.propertyField(fieldName, name, sensitivity.luceneFieldSuffix());
            if (IndexMetadata.hasOffsets(reader, luceneAltName)) {
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
        alternatives.add(matchSensitivity);
    
        // Update the sensitivity settings based on the alternatives we've seen so far.
        if (alternatives.contains(MatchSensitivity.SENSITIVE)) {
            if (alternatives.contains(MatchSensitivity.INSENSITIVE)) {
                if (alternatives.contains(MatchSensitivity.CASE_INSENSITIVE)) {
                    sensitivity = SensitivitySetting.CASE_AND_DIACRITICS_SEPARATE;
                } else {
                    sensitivity = SensitivitySetting.SENSITIVE_AND_INSENSITIVE;
                }
            } else {
                sensitivity = SensitivitySetting.ONLY_SENSITIVE;
            }
        } else {
            sensitivity = SensitivitySetting.ONLY_INSENSITIVE;
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
