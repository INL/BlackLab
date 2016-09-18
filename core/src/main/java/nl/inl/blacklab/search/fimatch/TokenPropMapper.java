package nl.inl.blacklab.search.fimatch;

public abstract class TokenPropMapper {

	/**
	 * Get the index number corresponding to the given property name.
	 *
	 * @param propertyName property to get the index for
	 * @return index for this property
	 */
	public abstract int getPropertyNumber(String propertyName);

	public abstract int getTermNumber(int propertyNumber, String propertyValue);

	public abstract int getTermAtPosition(int propertyNumber, int pos);

}
