package nl.inl.blacklab.search;

import java.util.ArrayList;
import java.util.List;

import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.indexstructure.ComplexFieldDesc;
import nl.inl.blacklab.search.indexstructure.PropertyDesc;
import nl.inl.util.StringUtil;

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
	 * @param searcher the searcher object
	 * @param fieldName the (complex) field to search
	 * @param propName the property to search
	 * @param caseSensitive whether search defaults to case-sensitive
	 * @param diacriticsSensitive whether search defaults to diacritics-sensitive
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

	public String optDesensitize(String value) {

		final String s = ComplexFieldUtil.SENSITIVE_ALT_NAME;
		final String i = ComplexFieldUtil.INSENSITIVE_ALT_NAME;
		final String ci = ComplexFieldUtil.CASE_INSENSITIVE_ALT_NAME;
		final String di = ComplexFieldUtil.DIACRITICS_INSENSITIVE_ALT_NAME;

		String[] parts = ComplexFieldUtil.getNameComponents(luceneField());

		String alt = parts.length >= 3 ? parts[2] : "";
		if (alt.equals(s)) {
			// Don't desensitize
			return value;
		}
		if (alt.equals(i)) {
			// Fully desensitize;
			return StringUtil.removeAccents(value).toLowerCase();
		}
		if (alt.equals(ci)) {
			// Only case-insensitive
			return value.toLowerCase();
		}
		if (alt.equals(di)) {
			// Only diacritics-insensitive
			return StringUtil.removeAccents(value);
		}

		// Unknown alternative; don't change value
		return value;
	}

	/**
	 * Return alternatives for the current field/prop that
	 * exist and are appropriate for our current settings.
	 *
	 * @return the alternatives that exist, in order of appropriateness
	 */
	private String[] getAlternatives() {

		if (searcher == null) {
			// Test
			if (caseSensitive)
				return new String[] {"s", "i"};
			return new String[] {"i", "s"};
		}

		final String s = ComplexFieldUtil.SENSITIVE_ALT_NAME;
		final String i = ComplexFieldUtil.INSENSITIVE_ALT_NAME;
		final String ci = ComplexFieldUtil.CASE_INSENSITIVE_ALT_NAME;
		final String di = ComplexFieldUtil.DIACRITICS_INSENSITIVE_ALT_NAME;

		ComplexFieldDesc cfd = searcher.getIndexStructure().getComplexFieldDesc(fieldName);
		if (cfd == null)
			return null;

		// Find the property
		PropertyDesc pd = cfd.getPropertyDesc(propName);
		SensitivitySetting sensitivity = pd.getSensitivity();
//		Collection<String> availableAlternatives = Collections.emptyList();
//		if (pd != null) {
//			availableAlternatives = pd.getAlternatives();
//		}

		// New alternative naming scheme (every alternative has a name)
		List<String> validAlternatives = new ArrayList<String>();
		if (!caseSensitive && !diacriticsSensitive) {
			// search insensitive if available
			if (sensitivity != SensitivitySetting.ONLY_SENSITIVE)
				validAlternatives.add(i);
			if (sensitivity != SensitivitySetting.ONLY_INSENSITIVE)
				validAlternatives.add(s);
		} else if (caseSensitive && diacriticsSensitive) {
			// search fully-sensitive if available
			if (sensitivity != SensitivitySetting.ONLY_INSENSITIVE)
				validAlternatives.add(s);
			if (sensitivity != SensitivitySetting.ONLY_SENSITIVE)
				validAlternatives.add(i);
		} else if (!diacriticsSensitive) {
			// search case-sensitive if available
			if (sensitivity == SensitivitySetting.CASE_AND_DIACRITICS_SEPARATE)
				validAlternatives.add(di);
			if (sensitivity != SensitivitySetting.ONLY_INSENSITIVE)
				validAlternatives.add(s);
			if (sensitivity != SensitivitySetting.ONLY_SENSITIVE)
				validAlternatives.add(i);
		} else {
			// search diacritics-sensitive if available
			if (sensitivity == SensitivitySetting.CASE_AND_DIACRITICS_SEPARATE)
				validAlternatives.add(ci);
			if (sensitivity != SensitivitySetting.ONLY_SENSITIVE)
				validAlternatives.add(i);
			if (sensitivity != SensitivitySetting.ONLY_INSENSITIVE)
				validAlternatives.add(s);
		}
		return validAlternatives.toArray(new String[] {});
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
	 * @param includeAlternative if true, also includes the default alternative at the end of the field name (alternatives determine
	 *   stuff like case-/diacritics-sensitivity).
	 * @return null if field, property or alternative not found; valid Lucene field name otherwise
	 */
	public String luceneField(boolean includeAlternative) {

		// Determine available alternatives based on sensitivity preferences.
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
			if (pd.hasAlternative(alt)) {
				// NOTE: is this loop necessary at all? getAlternatives() only
				//  returns available alternatives, so the first one should always
				//  be okay, right?
				return ComplexFieldUtil.propertyField(fieldName, propName, alt);
			}
		}

		// No valid alternative found. Use plain property.
		// NOTE: should never happen, and doesn't make sense anymore as there are
		// no 'plain properties' anymore.
		return ComplexFieldUtil.propertyField(fieldName, propName);
	}

	/**
	 * Get a simple execution context for a field. Used for
	 * testing/debugging purposes.
	 *
	 * @param fieldName field to get an execution context for
	 * @return the context
	 */
	public static QueryExecutionContext getSimple(String fieldName) {
		String mainPropName = ComplexFieldUtil.getDefaultMainPropName();
		return new QueryExecutionContext(null, fieldName, mainPropName, false, false);
	}

	public boolean alwaysHasClosingToken() {
		return searcher.getIndexStructure().alwaysHasClosingToken();
	}


}