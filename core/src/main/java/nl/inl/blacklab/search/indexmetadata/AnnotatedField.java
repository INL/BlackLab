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
	
	default Annotation mainAnnotation() {
	    return annotations().main();
	}
	
	default Annotation annotation(String name) {
	    return annotations().get(name);
	}

	boolean hasLengthTokens();

	boolean hasXmlTags();

    boolean hasPunctuationForwardIndex();

    String tokenLengthField();

    @Override
    default String contentsFieldName() {
        Annotation main = mainAnnotation();
        AnnotationSensitivity offsetsSensitivity = main.offsetsSensitivity();
        if (offsetsSensitivity == null)
            offsetsSensitivity = main.sensitivity(MatchSensitivity.SENSITIVE);
        return offsetsSensitivity.luceneField();
    }

    /**
     * Was the field that stores the length of this field in tokens
     * indexed with DocValues?
     * 
     * @return true if it was, false if not
     */
    boolean hasTokenLengthDocValues();

    default boolean isDummyFieldToStoreLinkedDocuments() {
        return annotations().isEmpty();
    }

}
