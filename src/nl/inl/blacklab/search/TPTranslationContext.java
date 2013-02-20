package nl.inl.blacklab.search;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.IndexStructure.AltDesc;
import nl.inl.blacklab.search.IndexStructure.ComplexFieldDesc;
import nl.inl.blacklab.search.IndexStructure.PropertyDesc;

/**
 * Represents the current "translation context" while translating a TextPattern
 * into a query. Inside a query, the context may change: a different property may
 * be "selected" to search in, the case sensitivity setting may change, etc. This
 * object is passed to the translation methods and keeps track of this context.
 */
public class TPTranslationContext {
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
	 * Construct a translation context object.
	 * @param fieldName the (complex) field to search
	 * @param propName the property to search
	 */
	public TPTranslationContext(Searcher searcher, String fieldName, String propName, boolean caseSensitive, boolean diacriticsSensitive) {
		this.searcher = searcher;
		this.fieldName = fieldName;
		this.propName = propName;
		this.caseSensitive = caseSensitive;
		this.diacriticsSensitive = diacriticsSensitive;
	}

	/**
	 * Return a new translation context with a different property selected.
	 * @param newPropName the property to select
	 * @return the new context
	 */
	public TPTranslationContext withProperty(String newPropName) {
		return new TPTranslationContext(searcher, fieldName, newPropName, caseSensitive, diacriticsSensitive);
	}

	public TPTranslationContext withSensitive(boolean caseSensitive_, boolean diacriticsSensitive_) {
		return new TPTranslationContext(searcher, fieldName, propName, caseSensitive_, diacriticsSensitive_);
	}

	public String[] getAlternatives() {
		if (!caseSensitive && !diacriticsSensitive)
			return null; // no alternatives
		if (caseSensitive && diacriticsSensitive)
			return new String[] {"s", "cs", "ds", ""}; // search fully-sensitive if available
		if (caseSensitive)
			return new String[] {"cs", "s", ""}; // search case-sensitive if available

		return new String[] {"ds", "s", ""}; // search diacritics-sensitive if available
	}

	/**
	 * Returns the correct current Lucene field name to use, based on the complex field name,
	 * property name and list of alternatives.
	 * @return null if field, property or alternative not found; valid Lucene field name otherwise
	 */
	public String luceneField() {

		// Determine preferred alternatives based on sensitivity preferences.
		String[] alternatives = getAlternatives();

		if (searcher == null) {
			// Mostly for testing. Don't check, just combine field parts.
			if (alternatives == null || alternatives.length == 0)
				return ComplexFieldUtil.fieldName(fieldName, propName);
			return ComplexFieldUtil.fieldName(fieldName, propName, alternatives[0]);
		}

		// Find the field and the property.
		ComplexFieldDesc cfd = searcher.getIndexStructure().getComplexFieldDesc(fieldName);
		if (cfd == null)
			return null;

		if (ComplexFieldUtil.BOOKKEEPING_SUBFIELDS.contains(propName)) {
			// Not a property but a bookkeeping subfield; ok, return it
			return ComplexFieldUtil.fieldName(fieldName, propName);
		}

		// Find the property
		PropertyDesc pd = cfd.getPropertyDesc(propName);
		if (pd == null)
			return null; // doesn't exist

		if (alternatives == null || alternatives.length == 0) {
			// Don't use any alternatives
			return ComplexFieldUtil.fieldName(fieldName, propName);
		}

		// Find the first available alternative to use
		for (String alt: alternatives) {
			AltDesc ad = pd.getAlternativeDesc(alt);
			if (ad != null)
				return ComplexFieldUtil.fieldName(fieldName, propName, alt);
		}

		// No valid alternative found.
		return null;
	}

	public static TPTranslationContext getSimple(String fieldName) {
		return new TPTranslationContext(null, fieldName, "", false, false);
	}


}