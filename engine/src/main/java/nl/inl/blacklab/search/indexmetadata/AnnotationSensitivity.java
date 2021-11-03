package nl.inl.blacklab.search.indexmetadata;

/**
 * An annotation on a field with a specific sensitivity.
 * 
 * This defines a Lucene field in the BlackLab index.
 */
public interface AnnotationSensitivity {
	
	Annotation annotation();
	
	MatchSensitivity sensitivity();
	
	default String luceneField() {
		return AnnotatedFieldNameUtil.annotationSensitivity(annotation().luceneFieldPrefix(), sensitivity().luceneFieldSuffix());
	}
}
