package nl.inl.blacklab.search.indexmetadata;

import nl.inl.blacklab.search.BlackLabIndex;

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

    @Deprecated
	default boolean hasXmlTags() { return hasRelationAnnotation(); }

    boolean hasRelationAnnotation();

    RelationsStats getRelationsStats(BlackLabIndex index, long limitValues);

    /**
     * Returns the Lucene field that contains the length (in tokens) of this field,
     * or null if there is no such field.
     *
     * @return the field name or null if lengths weren't stored
     */
    default String tokenLengthField() {
        return AnnotatedFieldNameUtil.lengthTokensField(name());
    }

    /**
     * For parallel corpora: the field with the offset at which the doc version starts.
     * @return start offset field
     */
    default String docStartOffsetField() {
        return AnnotatedFieldNameUtil.docStartOffsetField(name());
    }

    /**
     * For parallel corpora: the field with the offset at which the doc version ends.
     * @return end offset field
     */
    default String docEndOffsetField() {
        return AnnotatedFieldNameUtil.docEndOffsetField(name());
    }

    @Override
    default String contentsFieldName() {
        Annotation main = mainAnnotation();
        AnnotationSensitivity offsetsSensitivity = main.offsetsSensitivity();
        if (offsetsSensitivity == null)
            offsetsSensitivity = main.sensitivity(MatchSensitivity.SENSITIVE);
        return offsetsSensitivity.luceneField();
    }
}
