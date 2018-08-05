package nl.inl.blacklab.interfaces.struct;

import java.util.Collection;

/** An annotated field (formerly "complex field") */
public interface AnnotatedField extends Field {

	/**
	 * Get the set of property names for this complex field.
	 *
	 * Properties are returned sorted according to the displayOrder defined in the
	 * index metadata, if any.
	 *
	 * @return the set of annotations
	 */
	public Collection<Annotation> annotations();

	/**
	 * Get an annotation.
	 * 
	 * @param name name of the annotation
	 * @return annotation, or null if it doesn't exist
	 */
	public Annotation annotation(String name);

	public Annotation mainAnnotation();

	public boolean hasLengthTokens();

	public boolean hasXmlTags();

}
