package nl.inl.blacklab.search.indexmetadata.nint;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;

/**
 * An annotation on a field with a specific sensitivity.
 * 
 * This defines a Lucene field in the BlackLab index.
 */
public interface AnnotationSensitivity {
	
	Annotation annotation();
	
	MatchSensitivity sensitivity();
	
	default String luceneField() {
		return ComplexFieldUtil.propertyAlternative(annotation().luceneFieldPrefix(), sensitivity().luceneFieldSuffix());
	}
}
