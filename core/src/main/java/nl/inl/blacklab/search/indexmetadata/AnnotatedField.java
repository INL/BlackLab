package nl.inl.blacklab.search.indexmetadata;

/** An annotated field (formerly "complex field") */
public interface AnnotatedField extends Field {

	/**
	 * Get the annotations for this field.
	 *
	 * Properties are returned sorted according to the displayOrder defined in the
	 * index metadata, if any.
	 *
	 * @return the annotations
	 */
	Annotations annotations();

	boolean hasLengthTokens();

	boolean hasXmlTags();

    boolean hasPunctuationForwardIndex();

    String tokenLengthField();

}
