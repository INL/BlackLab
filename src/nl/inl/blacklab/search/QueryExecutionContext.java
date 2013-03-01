package nl.inl.blacklab.search;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.IndexStructure.AltDesc;
import nl.inl.blacklab.search.IndexStructure.ComplexFieldDesc;
import nl.inl.blacklab.search.IndexStructure.PropertyDesc;

/**
 * Represents the current "execution context" for executing a TextPattern query.
 * Inside a query, this context may change: a different property may
 * be "selected" to search in, the case sensitivity setting may change, etc. This
 * object is passed to the translation methods and keeps track of this context.
 */
public class QueryExecutionContext {
	/** The searcher object, representing the BlackLab index */
	public Searcher searcher;

	/** The (complex) field to search */
	public String fieldName;

	/** The property to search */
	public String propName;

	/** Search case-sensitive?
	 *
	 * NOTE: depending on available alternatives in the index, this will choose
	 * the most appropriate one.
	 */
	public boolean caseSensitive;

	/** Search diacritics-sensitive?
	 *
	 * NOTE: depending on available alternatives in the index, this will choose
	 * the most appropriate one.
	 */
	public boolean diacriticsSensitive;

	/**
	 * Construct a query execution context object.
	 * @param fieldName the (complex) field to search
	 * @param propName the property to search
	 */
	public QueryExecutionContext(Searcher searcher, String fieldName, String propName, boolean caseSensitive, boolean diacriticsSensitive) {
		this.searcher = searcher;
		this.fieldName = fieldName;
		this.propName = propName;
		this.caseSensitive = caseSensitive;
		this.diacriticsSensitive = diacriticsSensitive;
	}

	/**
	 * Return a new query execution context with a different property selected.
	 * @param newPropName the property to select
	 * @return the new context
	 */
	public QueryExecutionContext withProperty(String newPropName) {
		return new QueryExecutionContext(searcher, fieldName, newPropName, caseSensitive, diacriticsSensitive);
	}

	public QueryExecutionContext withSensitive(boolean caseSensitive_, boolean diacriticsSensitive_) {
		return new QueryExecutionContext(searcher, fieldName, propName, caseSensitive_, diacriticsSensitive_);
	}

	public String[] getAlternatives() {

		final String s = ComplexFieldUtil.SENSITIVE_ALT_NAME;
		final String i = ComplexFieldUtil.INSENSITIVE_ALT_NAME;
		final String ci = ComplexFieldUtil.CASE_INSENSITIVE_ALT_NAME;
		final String di = ComplexFieldUtil.DIACRITICS_INSENSITIVE_ALT_NAME;

		if (ComplexFieldUtil.isMainAlternativeNameless())  {
			// Old alternative naming scheme
			if (!caseSensitive && !diacriticsSensitive)
				return new String[] {i, ""}; // insensitive
			if (caseSensitive && diacriticsSensitive)
				return new String[] {s, ""}; // search fully-sensitive if available
			if (!diacriticsSensitive)
				return new String[] {di, s, i, ""}; // search case-sensitive if available

			return new String[] {ci, s, i, ""}; // search diacritics-sensitive if available
		}

		// New alternative naming scheme (every alternative has a name)
		if (!caseSensitive && !diacriticsSensitive)
			return new String[] {i, s}; // insensitive
		if (caseSensitive && diacriticsSensitive)
			return new String[] {s, i}; // search fully-sensitive if available
		if (!diacriticsSensitive)
			return new String[] {di, s, i}; // search case-sensitive if available

		return new String[] {ci, s, i}; // search diacritics-sensitive if available
	}

	/**
	 * Returns the correct current Lucene field name to use, based on the complex field name,
	 * property name and list of alternatives.
	 * @return null if field, property or alternative not found; valid Lucene field name otherwise
	 */
	public String luceneField() {
		return luceneField(true);
	}

	/**
	 * Returns the correct current Lucene field name to use, based on the complex field name,
	 * property name and list of alternatives.
	 * @return null if field, property or alternative not found; valid Lucene field name otherwise
	 */
	public String luceneField(boolean includeAlternative) {

		// Determine preferred alternatives based on sensitivity preferences.
		String[] alternatives = includeAlternative ? getAlternatives() : null;

		if (searcher == null) {
			// Mostly for testing. Don't check, just combine field parts.
			if (alternatives == null || alternatives.length == 0)
				return ComplexFieldUtil.propertyField(fieldName, propName);
			return ComplexFieldUtil.propertyField(fieldName, propName, alternatives[0]);
		}

		// Find the field and the property.
		ComplexFieldDesc cfd = searcher.getIndexStructure().getComplexFieldDesc(fieldName);
		if (cfd == null)
			return null;

		if (ComplexFieldUtil.isBookkeepingSubfield(propName)) {
			// Not a property but a bookkeeping subfield (prob. starttag/endtag); ok, return it
			// (can be removed when old field naming scheme is removed)
			return ComplexFieldUtil.bookkeepingField(fieldName, propName);
		}

		// Find the property
		PropertyDesc pd = cfd.getPropertyDesc(propName);
		if (pd == null)
			return ComplexFieldUtil.propertyField(fieldName, propName); // doesn't exist? use plain property name

		if (alternatives == null || alternatives.length == 0) {
			// Don't use any alternatives
			return ComplexFieldUtil.propertyField(fieldName, propName);
		}

		// Find the first available alternative to use
		for (String alt: alternatives) {
			AltDesc ad = pd.getAlternativeDesc(alt);
			if (ad != null)
				return ComplexFieldUtil.propertyField(fieldName, propName, alt);
		}

		// No valid alternative found. Use plain property.
		return ComplexFieldUtil.propertyField(fieldName, propName);
	}

	public static QueryExecutionContext getSimple(String fieldName) {
		String mainPropName = ComplexFieldUtil.getDefaultMainPropName();
		return new QueryExecutionContext(null, fieldName, mainPropName, false, false);
	}


}