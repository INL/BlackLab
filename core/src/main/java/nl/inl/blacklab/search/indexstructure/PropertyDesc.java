package nl.inl.blacklab.search.indexstructure;

import java.util.Map;
import java.util.TreeMap;

import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.index.complex.ComplexFieldProperty.SensitivitySetting;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;

/** Description of a property */
public class PropertyDesc {
	/** The property name */
	private String propName;

	/** Any alternatives this property may have */
	private Map<String, AltDesc> alternatives;

	private boolean forwardIndex;

	/** What sensitivity alternatives (sensitive/insensitive for case & diacritics) are present */
	private SensitivitySetting sensitivity = SensitivitySetting.ONLY_SENSITIVE;

	/** Which of the alternatives is the main one (containing the offset info, if present) */
	private AltDesc offsetsAlternative;

	public PropertyDesc(String name) {
		propName = name;
		alternatives = new TreeMap<>();
		forwardIndex = false;
	}

	@Override
	public String toString() {
//		String altDesc = "";
//		String altList = StringUtil.join(alternatives.values(), "\", \"");
//		if (alternatives.size() > 1)
//			altDesc = ", with alternatives \"" + altList + "\"";
//		else if (alternatives.size() == 1)
//			altDesc = ", with alternative \"" + altList + "\"";
		String sensitivityDesc;
		switch (sensitivity) {
		case ONLY_SENSITIVE:
			sensitivityDesc = "sensitive only";
			break;
		case ONLY_INSENSITIVE:
			sensitivityDesc = "insensitive only";
			break;
		case SENSITIVE_AND_INSENSITIVE:
			sensitivityDesc = "sensitive and insensitive";
			break;
		case CASE_AND_DIACRITICS_SEPARATE:
			sensitivityDesc = "case/diacritics sensitivity separate";
			break;
		default:
			throw new IllegalArgumentException("Unknown sensitivity " + sensitivity.toString());
		}
		return (propName.length() == 0 ? "(default)" : propName)
				+ (forwardIndex ? " (+FI)" : "") + ", " + sensitivityDesc;
	}

	public boolean hasForwardIndex() {
		return forwardIndex;
	}

	void addAlternative(String name) {
		AltDesc altDesc = new AltDesc(name);
		alternatives.put(name, altDesc);

		// Update the sensitivity settings based on the alternatives we've seen so far.
		if (alternatives.containsKey("s")) {
			if (alternatives.containsKey("i")) {
				if (alternatives.containsKey("ci")) {
					sensitivity = SensitivitySetting.CASE_AND_DIACRITICS_SEPARATE;
				} else {
					sensitivity = SensitivitySetting.SENSITIVE_AND_INSENSITIVE;
				}
			} else {
				sensitivity = SensitivitySetting.ONLY_SENSITIVE;
			}
		} else {
			sensitivity = SensitivitySetting.ONLY_INSENSITIVE;
		}
	}

	void setForwardIndex(boolean b) {
		forwardIndex = b;
	}

	/** Get this property's name
	 * @return the name */
	public String getName() {
		return propName;
	}

	/**
	 * What sensitivity alternatives were indexed for this property?
	 * @return the sensitivity setting
	 */
	public SensitivitySetting getSensitivity() {
		return sensitivity;
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
	public String offsetsAlternative() {
		return offsetsAlternative == null ? null : offsetsAlternative.getName();
	}

	/**
	 * Does this property have the sensitivity alternative specified?
	 * @param alt name of the sensitivity alternative: s, i, ci, di.
	 * @return true if it exists, false if not
	 */
	public boolean hasAlternative(String alt) {
		if (alt.equals("s")) {
			return sensitivity != SensitivitySetting.ONLY_INSENSITIVE;
		} else if (alt.equals("i")) {
			return sensitivity != SensitivitySetting.ONLY_SENSITIVE;
		} else if (alt.equals("ci") || alt.equals("di")) {
			return sensitivity == SensitivitySetting.CASE_AND_DIACRITICS_SEPARATE;
		}
		throw new IllegalArgumentException("Unknown sensitivity alternative ' " + alt + "'! Valid: s, i, ci, di");
	}
}