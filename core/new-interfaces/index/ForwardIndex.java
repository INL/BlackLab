package nl.inl.blacklab.interfaces.index;

import java.util.Collection;

import nl.inl.blacklab.interfaces.struct.Annotation;

/**
 * Represents a single forward index where multiple
 * annotations are stored for each document.
 * 
 * Right now, this is an idealized view of the actual situation:
 * separate forward indices, one for each annotations.
 * 
 * In the future, having this interface may allow us to experiment with 
 * how forward indices are stored to be more document-centric instead 
 * of the current annotation-centric approach, which could improve performance 
 * in certain cases because of disk caching.
 * 
 * Right now, a potential issue is that each annotation gets its own fiid,
 * but if all forward index access were to go through this interface, we could
 * ensure that these ids are all identical, and in the future, we could eliminate 
 * the need for separate ids per annotation.
 */
public interface ForwardIndex extends DocStore<ForwardIndexDoc> {
	
	/**
	 * Get a single-annotation view of the forward index.
	 * 
	 * @param annotation what annotation to get a view for
	 * @return single-annotation view of the forward index
	 */
	AnnotationForwardIndex singleAnnotationView(Annotation annotation);
	
	/**
	 * Return the annotations present in the forward index
	 * @return annotation names
	 */
	Collection<Annotation> annotations();
	
	/**
	 * Get the Terms object in order to translate ids to token strings
	 * @param annotation annotation for which to retrieve the Terms object
	 * @return the Terms object
	 */
	Terms terms(Annotation annotation);
	
}
