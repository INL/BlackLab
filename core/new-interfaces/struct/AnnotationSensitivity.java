package nl.inl.blacklab.interfaces.struct;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.interfaces.MatchSensitivity;

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
