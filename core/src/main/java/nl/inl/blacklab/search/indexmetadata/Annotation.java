package nl.inl.blacklab.search.indexmetadata;

import java.util.Collection;

/** An annotation on an annotated field. */
public interface Annotation {
	
	/** @return field for which this is an annotation */
	AnnotatedField field();

	/** @return this annotation's name */
	String name();

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
	 * Describe what sensitivities are available.
	 * 
	 * Used in BLS. Replaces old SensitivitySetting enum.
	 * 
	 * @return description of the available sensitivities, e.g. "ONLY_INSENSITIVE"
	 */
    String sensitivitySettingDesc();

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

    
}
