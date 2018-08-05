package nl.inl.blacklab.interfaces.index;

/**
 * Forward index for a single annotation.
 * 
 * This was our regular ForwardIndex class. In the future, it will likely be a
 * single-annotation view on the "multi"-forward index. This may even disappear
 * eventually.
 */
public interface AnnotationForwardIndex extends DocStore<AnnotationForwardIndexDoc> {
	
	/**
	 * Get the Terms object in order to translate ids to token strings
	 * @return the Terms object
	 */
	Terms terms();
	
}
