package nl.inl.blacklab.search.indexmetadata;

/** An annotated field */
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

	boolean hasXmlTags();

	String tokenLengthField();

    @Override
    default String contentsFieldName() {
        Annotation main = mainAnnotation();
        AnnotationSensitivity offsetsSensitivity = main.offsetsSensitivity();
        if (offsetsSensitivity == null)
            offsetsSensitivity = main.sensitivity(MatchSensitivity.SENSITIVE);
        return offsetsSensitivity.luceneField();
    }

	default Annotation tagsAnnotation() {
		return hasXmlTags() ? annotation(AnnotatedFieldNameUtil.TAGS_ANNOT_NAME): null;
	}

	default Annotation punctAnnotation() {
		return hasXmlTags() ? annotation(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME): null;
	}
}
