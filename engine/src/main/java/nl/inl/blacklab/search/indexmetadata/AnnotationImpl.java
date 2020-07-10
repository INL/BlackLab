package nl.inl.blacklab.search.indexmetadata;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/** Annotation on a field. */
class AnnotationImpl implements Annotation, Freezable<AnnotationImpl> {
    
    private IndexMetadata indexMetadata;
    
    /** The field this is an annotation for. */
    private AnnotatedField field;
    
    /** The annotation name */
    private String name;

    /** Name to display in user interface (optional) */
    String displayName;

    /** Description for user interface (optional) */
    private String description = "";

    /**
     * What UI element to use for this annotation (e.g. text, select); only used in
     * frontend, ignored by BlackLab itself.
     */
    private String uiType = "";
    
    /**
     * Is this an internal annotation, not relevant for a search interface (e.g. punct, starttag)
     */
    private boolean isInternal;

    /** Reference to match sensitivities this annotation has */
    private Set<AnnotationSensitivity> alternatives = new HashSet<>();

    /** Match sensitivity values this annotation has */
    private Set<MatchSensitivity> matchSensitivities = new HashSet<>();

    /** Whether or not this annotation has a forward index. */
    private boolean forwardIndex;

    /**
     * Which of the alternatives is the main one (containing the offset info, if
     * present)
     */
    private AnnotationSensitivity offsetsAlternative;

    private boolean frozen = false;
    
    /** Names of our subannotations, if declared (new-style index) and if we have any */
    private Set<String> subAnnotationNames = new HashSet<>();

    /** Our subannotations (if we have an old-style index, where subannotations aren't declared).
     *  This is not actually considered state, just cache, because all 
     *  subannotations are valid (we don't know which ones were indexed).
     */
    Map<String, Subannotation> cachedSubs = new HashMap<>();
    
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
        return Collections.unmodifiableSet(alternatives);
    }
    
    @Override
    public boolean hasSensitivity(MatchSensitivity sensitivity) {
        return matchSensitivities.contains(sensitivity);
    }

    @Override
    public AnnotationSensitivity sensitivity(MatchSensitivity sensitivity) {
        if (!hasSensitivity(sensitivity))
            throw new UnsupportedOperationException("Specified sensitivity " + sensitivity + " not present for field " + luceneFieldPrefix());
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
        return isInternal;
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
     * @param fieldName the field this annotation belongs under
     * @return true if found, false if not
     */
    public boolean detectOffsetsSensitivity(IndexReader reader, String fieldName) {
        ensureNotFrozen();
        // Iterate over the alternatives and for each alternative, find a term
        // vector. If that has character offsets stored, it's our main annotation.
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
    }

    AnnotationImpl setForwardIndex(boolean b) {
        ensureNotFrozen();
        forwardIndex = b;
        return this;
    }

    public AnnotationImpl setName(String propName) {
        ensureNotFrozen();
        this.name = propName;
        this.isInternal |= nameImpliesInternal();
        return this;
    }
    
    public AnnotationImpl setInternal() {
        ensureNotFrozen();
        this.isInternal = true;
        return this;
    }

    private boolean nameImpliesInternal() {
        return name != null && (name.equals(AnnotatedFieldNameUtil.TAGS_ANNOT_NAME) ||
                name.equals(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME));
    }

    public AnnotationImpl setUiType(String uiType) {
        ensureNotFrozen();
        this.uiType = uiType;
        return this;
    }

    public AnnotationImpl setDescription(String description) {
        ensureNotFrozen();
        this.description = description;
        return this;
    }
    
    @Override
    public AnnotationImpl freeze() {
        this.frozen = true;
        return this;
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
        this.offsetsAlternative = sensitivity(offsetsAlternative);
    }

    @Override
    public Annotation subannotation(String subName) {
        if (!indexMetadata().subannotationsStoredWithParent())
            throw new BlackLabRuntimeException("Can only call this for old-style indexes");
        Subannotation subAnnotation = cachedSubs.get(subName);
        if (subAnnotation == null) {
            subAnnotation = new Subannotation(indexMetadata, this, subName);
            cachedSubs.put(subName, subAnnotation);
        }
        return subAnnotation;
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
    public String subName() {
        throw new BlackLabRuntimeException("Only valid for old-style indexes");
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

}
