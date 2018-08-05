package nl.inl.blacklab.interfaces.struct;

import java.util.Collection;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.interfaces.MatchSensitivity;

/** An annotation on an annotated field. */
public interface Annotation {
	
	/** @return field for which this is an annotation */
	public AnnotatedField field();

	/** @return this annotation's name */
	public String name();

    public String uiType();

    public String description();

	public boolean hasForwardIndex();

	/**
	 * Return which sensitivity-indexing contains character offset information.
	 *
	 * Note that there may not be such a sensitivity-indexing.
	 *
	 * @return the alternative, or null if there is none.
	 */
	public AnnotationSensitivity offsetsField();

	/**
	 * What sensitivity alternatives were indexed for this property?
	 * @return the sensitivity setting
	 */
	public Collection<MatchSensitivity> sensitivities();

	/**
	 * Was this annotation indexed with the sensitivity specified?
	 * @param sensitivity desired sensitivity
	 * @return true if yes, false if not
	 */
	public boolean hasSensitivity(MatchSensitivity sensitivity);
	
	/**
	 * Get a reference to the specified sensitivity.
	 * 
	 * @param sensitivity desired sensitivity
	 * @return reference to the specified sensitivity.
	 */
	public AnnotationSensitivity sensitivity(MatchSensitivity sensitivity);

    public String displayName();

    /**
     * Is this 'annotation' internal to BlackLab?
     * 
     * Some 'annotations' are not really annotations from the input files,
     * but internal information for BlackLab. These shouldn't be shown in the
     * user interface.
     *  
     * @return true if the annotation is internal
     */
    public boolean isInternal();

    /**
     * Return the Lucene field prefix for this annotation.
     * @return Lucene field prefix
     */
	default String luceneFieldPrefix() {
		return ComplexFieldUtil.propertyField(field().name(), name());
	}

}
