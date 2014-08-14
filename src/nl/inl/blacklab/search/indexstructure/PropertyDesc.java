package nl.inl.blacklab.search.indexstructure;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.util.StringUtil;

import org.apache.lucene.index.IndexReader;

/** Description of a property */
public class PropertyDesc {
	/** The property name */
	private String propName;

	/** Any alternatives this property may have */
	private Map<String, AltDesc> alternatives;

	private boolean forwardIndex;

	/** Which of the alternatives is the main one (containing the offset info, if present) */
	private AltDesc offsetsAlternative;

	public PropertyDesc(String name) {
		propName = name;
		alternatives = new TreeMap<String, AltDesc>();
		forwardIndex = false;
	}

	@Override
	public String toString() {
		String altDesc = "";
		String altList = StringUtil.join(alternatives.values(), "\", \"");
		if (alternatives.size() > 1)
			altDesc = ", with alternatives \"" + altList + "\"";
		else if (alternatives.size() == 1)
			altDesc = ", with alternative \"" + altList + "\"";
		return (propName.length() == 0 ? "(default)" : propName)
				+ (forwardIndex ? " (+FI)" : "") + altDesc;
	}

	public boolean hasForwardIndex() {
		return forwardIndex;
	}

	public void addAlternative(String name) {
		AltDesc altDesc = new AltDesc(name);
		alternatives.put(name, altDesc);
	}

	void setForwardIndex(boolean b) {
		forwardIndex = b;
	}

	/** Get this property's name
	 * @return the name */
	public String getName() {
		return propName;
	}

	/** Get the set of names of alternatives for this property
	 * @return the names
	 */
	public Collection<String> getAlternatives() {
		return alternatives.keySet();
	}

	/**
	 * Get an alternative's description.
	 * @param name name of the alternative
	 * @return the description
	 */
	public AltDesc getAlternativeDesc(String name) {
		if (!alternatives.containsKey(name))
			throw new RuntimeException("Alternative '" + name + "' not found!");
		return alternatives.get(name);
	}

	/**
	 * Detect which alternative is the one containing character offsets.
	 *
	 * Note that there may not be such an alternative.
	 *
	 * @param reader the index reader
	 * @param fieldName the field this property belongs under
	 * @return true if found, false if not
	 */
	public boolean detectOffsetsAlternative(IndexReader reader, String fieldName) {
		// Iterate over the alternatives and for each alternative, find a term
		// vector. If that has character offsets stored, it's our main property.
		// If not, keep searching.
		for (AltDesc alt: alternatives.values()) {
			String luceneAltName = ComplexFieldUtil.propertyField(fieldName, propName,
					alt.getName());
			if (IndexStructure.hasOffsets(reader, luceneAltName)) {
				offsetsAlternative = alt;
				return true;
			}
		}

		return false;
	}

	/**
	 * Return which alternative contains character offset information.
	 *
	 * Note that there may not be such an alternative.
	 *
	 * @return the alternative, or null if there is none.
	 */
	public AltDesc getOffsetsAlternative() {
		return offsetsAlternative;
	}
}