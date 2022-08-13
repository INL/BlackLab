package nl.inl.blacklab.search.indexmetadata;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.index.annotated.AnnotationSensitivities;
import nl.inl.util.LuceneUtil;

/** Annotation on a field. */
public class AnnotationImpl implements Annotation, Freezable<AnnotationImpl> {
    
    private final IndexMetadata indexMetadata;
    
    /** The field this is an annotation for. */
    private final AnnotatedField field;
    
    /** The annotation name */
    private String name;

    /** Our custom properties */
    private final CustomPropsMap custom = new CustomPropsMap();

    /**
     * Is this an internal annotation, not relevant for a search interface (e.g. punct, starttag)
     */
    private boolean isInternal;

    /** Reference to match sensitivities this annotation has */
    private final Map<MatchSensitivity, AnnotationSensitivity> alternatives = new LinkedHashMap<>();

    /** Whether or not this annotation has a forward index. */
    private boolean forwardIndex;

    /**
     * Which of the alternatives is the main one (containing the offset info, if
     * present)
     */
    private AnnotationSensitivity offsetsAlternative;

    private boolean frozen = false;
    
    /** Names of our subannotations, if we have any */
    private final Set<String> subAnnotationNames = new HashSet<>();
    
    /**
     * If this is a subannotation, what is its parent annotation?
     */
    private Annotation mainAnnotation = null;

    AnnotationImpl(IndexMetadata indexMetadata, AnnotatedField field) {
        this(indexMetadata, field, null);
    }

    AnnotationImpl(IndexMetadata indexMetadata, AnnotatedField field, String name) {
        this.indexMetadata = indexMetadata;
        this.field = field;
        this.setName(name);
        forwardIndex = false;
    }
    
    @Override
    public IndexMetadata indexMetadata() {
        return indexMetadata;
    }
    
    @Override
    public AnnotatedField field() {
        return field;
    }
    
    @Override
    public String toString() {
        return luceneFieldPrefix();
    }

    @Override
    public boolean hasForwardIndex() {
        return forwardIndex;
    }

    /**
     * Get this annotation's name
     * 
     * @return the name
     */
    @Override
    public String name() {
        return name;
    }

    @Override
    public Collection<AnnotationSensitivity> sensitivities() {
        return Collections.unmodifiableCollection(alternatives.values());
    }
    
    @Override
    public boolean hasSensitivity(MatchSensitivity sensitivity) {
        return alternatives.containsKey(sensitivity);
    }

    @Override
    public AnnotationSensitivity sensitivity(MatchSensitivity sensitivity) {
        AnnotationSensitivity s = alternatives.get(sensitivity);
        if (s == null)
            throw new UnsupportedOperationException("Specified sensitivity " + sensitivity + " not present for field " + luceneFieldPrefix());
        return s;
    }

    /**
     * Return which alternative contains character offset information.
     *
     * @return the alternative, or null if there is none.
     */
    @Override
    public AnnotationSensitivity offsetsSensitivity() {
        return offsetsAlternative;
    }

    /**
     * @deprecated use {@link #custom()} with .get("displayName", name) instead
     */
    @Deprecated
    @Override
    public String displayName() {
        String displayName = custom.get("displayName", "");
        if (!displayName.isEmpty())
            return displayName;
        return StringUtils.capitalize(name);
    }

    @Override
    public boolean isInternal() {
        return isInternal;
    }

    /**
     * @deprecated use {@link #custom()} with .get("uiType", name) instead
     */
    @Deprecated
    @Override
    public String uiType() {
        return custom.get("uiType", "");
    }

    /**
     * @deprecated use {@link #custom()} with .get("description", name) instead
     */
    @Deprecated
    @Override
    public String description() {
        return custom.get("description", "");
    }
    
    // Methods that mutate data
    // -----------------------------------------------------

    /**
     * Detect which alternative is the one containing character offsets.
     *
     * Note that there may not be such an alternative.
     *
     * @param reader the index reader
     * @return true if found, false if not
     */
    public boolean detectOffsetsSensitivity(IndexReader reader) {
        ensureNotFrozen();
        // Iterate over the alternatives and for each alternative, find a term
        // vector. If that has character offsets stored, it's our main annotation.
        // If not, keep searching.
        for (AnnotationSensitivity sensitivity: alternatives.values()) {
            if (LuceneUtil.hasOffsets(reader, sensitivity.luceneField())) {
                offsetsAlternative = sensitivity;
                return true;
            }
        }
    
        return false;
    }

    public void createSensitivities(AnnotationSensitivities sensitivitySetting) {
        if (sensitivitySetting == AnnotationSensitivities.DEFAULT)
            throw new IllegalArgumentException("Don't know what to do with DEFAULT sensitivity setting");
        if (sensitivitySetting == AnnotationSensitivities.CASE_AND_DIACRITICS_SEPARATE) {
            addAlternative(MatchSensitivity.CASE_INSENSITIVE);
            addAlternative(MatchSensitivity.DIACRITICS_INSENSITIVE);
        }
        if (sensitivitySetting != AnnotationSensitivities.ONLY_INSENSITIVE) {
            addAlternative(MatchSensitivity.SENSITIVE);
        }
        if (sensitivitySetting != AnnotationSensitivities.ONLY_SENSITIVE) {
            addAlternative(MatchSensitivity.INSENSITIVE);
        }
    }

    /**
     * @deprecated use {@link #custom()} with .put("displayName", ...) instead
     */
    @Deprecated
    public void setDisplayName(String displayName) {
        ensureNotFrozen();
        this.custom.put("displayName", displayName);
    }

    void addAlternative(MatchSensitivity matchSensitivity) {
        AnnotationSensitivity sensitivity = new AnnotationSensitivityImpl(this, matchSensitivity);
        if (!alternatives.containsKey(matchSensitivity)) {
            ensureNotFrozen();
            alternatives.put(matchSensitivity, sensitivity);
        }
    }

    public void setForwardIndex(boolean b) {
        if (forwardIndex != b) {
            ensureNotFrozen();
            forwardIndex = b;
        }
    }

    public void setName(String annotationName) {
        ensureNotFrozen();
        this.name = annotationName;
        this.isInternal |= nameImpliesInternal();
    }
    
    public void setInternal() {
        ensureNotFrozen();
        this.isInternal = true;
    }

    private boolean nameImpliesInternal() {
        return name != null && (name.equals(AnnotatedFieldNameUtil.TAGS_ANNOT_NAME) ||
                name.equals(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME));
    }

    /**
     * @deprecated use {@link #custom()} with .put("uiType", ...) instead
     */
    @Deprecated
    public void setUiType(String uiType) {
        ensureNotFrozen();
        custom.put("uiType", uiType);
    }


    /**
     * @deprecated use {@link #custom()} with .put("uiType", ...) instead
     */
    @Deprecated
    public void setDescription(String description) {
        ensureNotFrozen();
        custom.put("description", description);
    }
    
    @Override
    public void freeze() {
        this.frozen = true;
    }
    
    @Override
    public boolean isFrozen() {
        return this.frozen;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((field == null) ? 0 : field.hashCode());
        result = prime * result + ((name == null) ? 0 : name.hashCode());
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
        AnnotationImpl other = (AnnotationImpl) obj;
        if (field == null) {
            if (other.field != null)
                return false;
        } else if (!field.equals(other.field))
            return false;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }
    
    public void setOffsetsSensitivity(MatchSensitivity offsetsAlternative) {
        AnnotationSensitivity newValue = sensitivity(offsetsAlternative);
        if (this.offsetsAlternative == null || !this.offsetsAlternative.equals(newValue)) {
            ensureNotFrozen();
            this.offsetsAlternative = newValue;
        }
    }
    
    @Override
    public Set<String> subannotationNames() {
        return subAnnotationNames;
    }
    
    /**
     * Indicate that this is a subannotation.
     * 
     * @param parentAnnotation our parent annotation, e.g. "pos" for "pos_number"
     */
    @Override
    public void setSubAnnotation(Annotation parentAnnotation) {
        ensureNotFrozen();
        this.mainAnnotation = parentAnnotation;
    }

    @Override
    public boolean isSubannotation() {
        return mainAnnotation != null;
    }
    
    @Override
    public Annotation parentAnnotation() {
        return mainAnnotation;
    }

    public void setSubannotationNames(List<String> names) {
        subAnnotationNames.clear();
        subAnnotationNames.addAll(names);
    }

    public CustomPropsMap custom() {
        return custom;
    }
}
