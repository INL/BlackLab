package nl.inl.blacklab.search.indexmetadata;

import java.util.Collection;
import java.util.Set;

/** An annotation on an annotated field. */
public interface Annotation {

    IndexMetadata indexMetadata();
    
	/** @return field for which this is an annotation */
	AnnotatedField field();

	/** @return this annotation's name */
	String name();
	
	/** Get the subannotation name, if this is a subannotation.
	 * @return subannotation name, or null if this is not a subannotation
	 */
    String subName();
    
    /**
     * Is this a subannotation?
     * @return true if is, false if not
     */
    boolean isSubannotation();
    
    /**
     * If this is a subannotation, return its parent annotation.
     * 
     * @return parent annotation or null if this is not a subannotation
     */
    Annotation parentAnnotation();

    /**
     * What type of UI should be shown for this annotation?
     * 
     * Note that BlackLab doesn't do anything with this value; it is merely a convenience for the frontend.
     * 
     * @return desired UI type
     */
    String uiType();

    String description();

	boolean hasForwardIndex();

	/**
	 * Return which sensitivity-indexing contains character offset information.
	 *
	 * Note that there may not be such a sensitivity-indexing.
	 *
	 * @return the alternative, or null if there is none.
	 */
	AnnotationSensitivity offsetsSensitivity();

	/**
	 * What sensitivity alternatives were indexed for this annotation?
	 * @return the sensitivity setting
	 */
	Collection<AnnotationSensitivity> sensitivities();

	/**
	 * Was this annotation indexed with the sensitivity specified?
	 * @param sensitivity desired sensitivity
	 * @return true if yes, false if not
	 */
	boolean hasSensitivity(MatchSensitivity sensitivity);
	
	/**
	 * Get a reference to the specified sensitivity.
	 * 
	 * @param sensitivity desired sensitivity
	 * @return reference to the specified sensitivity.
	 */
	AnnotationSensitivity sensitivity(MatchSensitivity sensitivity);

    String displayName();

    /**
     * Is this 'annotation' internal to BlackLab?
     * 
     * Some 'annotations' are not really annotations from the input files,
     * but internal information for BlackLab. These shouldn't be shown in the
     * user interface.
     *  
     * @return true if the annotation is internal
     */
    boolean isInternal();

    /**
     * Return the Lucene field prefix for this annotation.
     * @return Lucene field prefix
     */
	default String luceneFieldPrefix() {
		return AnnotatedFieldNameUtil.annotationField(field().name(), name());
	}

    default String forwardIndexIdField() {
        return AnnotatedFieldNameUtil.forwardIndexIdField(luceneFieldPrefix());
    }
    
    /**
     * Get subannotation descriptor.
     * 
     * Only valid for old-style indexes! (IndexMetadata.subannotationsStoredWithParent() == true)
     * 
     * Note that subannotations are not (yet) declared in index structure,
     * so this will always succeed, even if the subannotation wasn't actually
     * indexed. In that case, no hits will be found.
     * 
     * @param subName subannotation name
     * @return subannotation descriptor
     */
    Annotation subannotation(String subName);

    /**
     * Get names of the subannotations for this annotation.
     * 
     * Only valid for new-style indexes! (IndexMetadata.subannotationsStoredWithParent() == false)
     * 
     * @return names of annotations that are considered subannotations of this annotation
     */
    Set<String> subannotationNames();
    
    @Override
    boolean equals(Object obj);
    
    @Override
    int hashCode();

    /**
     * Return prefix for the value we're searching for, if any.
     * 
     * We used to index subannotations in the same field as their parent annotation,
     * with the values prefixed. We don't do this anymore, but for old indexes,
     * this is still relevant.
     * 
     * @return the prefix
     */
    default String subpropValuePrefix() {
        if (isSubannotation() && indexMetadata().subannotationsStoredWithParent())
            return AnnotatedFieldNameUtil.SUBANNOTATION_SEPARATOR + subName() + AnnotatedFieldNameUtil.SUBANNOTATION_SEPARATOR;
        return "";
    }

    void setSubAnnotation(Annotation parentAnnotation);
    
}
