package nl.inl.blacklab.search;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.index.complex.ComplexFieldUtil.BookkeepFieldType;
import nl.inl.util.StringUtil;

import org.apache.log4j.Logger;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.TermFreqVector;
import org.apache.lucene.index.TermPositionVector;
import org.apache.lucene.util.ReaderUtil;

/**
 * Determines the structure of a BlackLab index.
 */
public class IndexStructure {
	protected static final Logger logger = Logger.getLogger(IndexStructure.class);

	/** Possible types of metadata fields. */
	public enum FieldType {
		TEXT, NUMERIC
	}

	/** Types of property alternatives */
	public enum AltType {
		UNKNOWN, SENSITIVE
	}

	/** Description of a complex field */
	public static class ComplexFieldDesc {

		/** Complex field's name */
		private String fieldName;

		/** This complex field's properties */
		private Map<String, PropertyDesc> props;

		/** The field's main property */
		private PropertyDesc mainProperty;

		/** Does the field have an associated content store? */
		private boolean contentStore;

		/** Is the field length in tokens stored? */
		private boolean lengthInTokens;

		/** Are there XML tag locations stored for this field? */
		private boolean xmlTags;

		public ComplexFieldDesc(String name) {
			fieldName = name;
			props = new TreeMap<String, PropertyDesc>();
			contentStore = false;
			lengthInTokens = false;
			xmlTags = false;
			mainProperty = null;
		}

		@Override
		public String toString() {
			return fieldName + " [" + StringUtil.join(props.values(), ", ") + "]";
		}

		/** Get this complex field's name
		 * @return this field's name */
		public String getName() {
			return fieldName;
		}

		/** Get the set of property names for this complex field
		 * @return the set of properties
		 */
		public Collection<String> getProperties() {
			return props.keySet();
		}

		/**
		 * Get a property description.
		 * @param name name of the property
		 * @return the description
		 */
		public PropertyDesc getPropertyDesc(String name) {
			return props.get(name);
		}

		public boolean hasContentStore() {
			return contentStore;
		}

		public boolean hasLengthTokens() {
			return lengthInTokens;
		}

		public boolean hasXmlTags() {
			return xmlTags;
		}

		/**
		 * Checks if this field has a "punctuation" forward index, storing all the
		 * intra-word characters (whitespace and punctuation) so we can build concordances
		 * directly from the forward indices.
		 * @return true iff there's a punctuation forward index.
		 */
		public boolean hasPunctuation() {
			PropertyDesc pd = props.get(ComplexFieldUtil.PUNCTUATION_PROP_NAME);
			return pd != null && pd.hasForwardIndex();
		}

		/**
		 * An index field was found and split into parts, and belongs
		 * to this complex field. See what type it is and update our
		 * fields accordingly.
		 * @param parts parts of the Lucene index field name
		 */
		void processIndexField(String[] parts) {

			// See if this is a builtin bookkeeping field or a property.
			if (parts.length == 1 && !ComplexFieldUtil.isMainPropertyNameless())
				throw new RuntimeException("Complex field with just basename given, error!");

			String propPart = parts.length == 1 ? "" : parts[1];

			if (propPart == null && parts.length >= 3) {
				// Bookkeeping field
				BookkeepFieldType bookkeepingFieldIndex = ComplexFieldUtil
						.whichBookkeepingSubfield(parts[3]);
				switch (bookkeepingFieldIndex) {
				case CONTENT_ID:
					// Complex field has content store
					contentStore = true;
					return;
				case FORWARD_INDEX_ID:
					// Main property has forward index
					getOrCreateProperty("").setForwardIndex(true);
					return;
				case LENGTH_TOKENS:
					// Complex field has length in tokens
					lengthInTokens = true;
					return;
				}
				throw new RuntimeException();
			}

			// Not a bookkeeping field; must be a property (alternative).
			PropertyDesc pd = getOrCreateProperty(propPart);
			if (pd.getName().equals(ComplexFieldUtil.START_TAG_PROP_NAME))
				xmlTags = true;
			if (parts.length > 2) {
				if (parts[2] != null) {
					// Alternative
					pd.addAlternative(parts[2]);
				} else if (parts.length >= 3) {
					// Property bookkeeping field
					if (parts[3].equals(ComplexFieldUtil.FORWARD_INDEX_ID_BOOKKEEP_NAME)) {
						pd.setForwardIndex(true);
					} else
						throw new RuntimeException("Unknown property bookkeeping field " + parts[3]);
				} else {
					// No alternative specified; guess we have nameless alternatives.
					ComplexFieldUtil._setMainAlternativeNameless(true);
					pd.addAlternative("");
				}
			}
		}

		private PropertyDesc getOrCreateProperty(String name) {
			PropertyDesc pd = props.get(name);
			if (pd == null) {
				pd = new PropertyDesc(name);
				props.put(name, pd);
			}
			return pd;
		}

		public PropertyDesc getMainProperty() {
			return mainProperty;
		}

		public void detectMainProperty(IndexReader reader) {
			for (PropertyDesc pr: props.values()) {
				if (pr.detectOffsetsAlternative(reader, fieldName)) {
					// This field has offsets stored. Must be the main prop field.
					mainProperty = pr;
					return;
				}
			}
			throw new RuntimeException(
					"No main property (with char. offsets) detected for complex field " + fieldName);
		}

		public void print(PrintStream out) {
			for (PropertyDesc pr: props.values()) {
				out.println("  * Property: " + pr.toString());
			}
			out.println("  * " + (contentStore ? "Includes" : "No") + " content store");
			out.println("  * " + (xmlTags ? "Includes" : "No") + " XML tag index");
			out.println("  * " + (lengthInTokens ? "Includes" : "No") + " document length field");
		}

	}

	/** Description of a property */
	public static class PropertyDesc {
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
				for (int n = 0; n < reader.maxDoc(); n++) {
					if (!reader.isDeleted(n)) {
						try {
							TermFreqVector tv = reader.getTermFreqVector(n, luceneAltName);
							if (tv == null) {
								// No term vector; probably not stored in this document.
								continue;
							}
							if (tv instanceof TermPositionVector) {
								if (((TermPositionVector) tv).getOffsets(0) != null) {
									// This field has offsets stored. Must be the main alternative.
									offsetsAlternative = alt;
									return true;
								}
								// This alternative has no offsets stored. Don't look at any more documents,
								// go to the next alternative.
								break;
							}
						} catch (IOException e) {
							throw new RuntimeException(e);
						}
					}
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

	/** Description of a property alternative */
	public static class AltDesc {
		/** name of this alternative */
		private String altName;

		/** type of this alternative */
		private AltType type;

		public AltDesc(String name) {
			altName = name;
			type = name.equals("s") ? AltType.SENSITIVE : AltType.UNKNOWN;
		}

		@Override
		public String toString() {
			return altName;
		}

		/** Get the name of this alternative
		 * @return the name
		 */
		public String getName() {
			return altName;
		}

		/** Get the type of this alternative
		 * @return the type
		 */
		public AltType getType() {
			return type;
		}
	}

	/** Our index */
	private IndexReader reader;

	/** All non-complex fields in our index (metadata fields) and their types. */
	private Map<String, FieldType> metadataFields;

	/** The complex fields in our index */
	private Map<String, ComplexFieldDesc> complexFields;

	/** The main contents field in our index. This is either the complex field with the name "contents",
	 *  or if that doesn't exist, the first complex field found. */
	private ComplexFieldDesc mainContentsField;

	/**
	 * Construct an IndexStructure object, querying the index for the available
	 * fields and their types.
	 * @param reader the index of which we want to know the structure
	 */
	public IndexStructure(IndexReader reader) {
		this.reader = reader;
		metadataFields = new TreeMap<String, FieldType>();
		complexFields = new TreeMap<String, ComplexFieldDesc>();

		FieldInfos fis = ReaderUtil.getMergedFieldInfos(reader);

		// Detect index naming scheme
		boolean isOldNamingScheme = true, avoidSpecialChars = true;
		for (int i = 0; i < fis.size(); i++) {
			FieldInfo fi = fis.fieldInfo(i);
			String name = fi.name;
			if (name.contains("%")) {
				isOldNamingScheme = false;
				avoidSpecialChars = false;
			}
			if (name.contains("_PR_")) {
				isOldNamingScheme = false;
				avoidSpecialChars = true;
			}
		}
		ComplexFieldUtil.setFieldNameSeparators(avoidSpecialChars, isOldNamingScheme);

		// reader.getFieldInfos();
		for (int i = 0; i < fis.size(); i++) {
			FieldInfo fi = fis.fieldInfo(i);
			String name = fi.name;

			// Parse the name to see if it is a metadata field or part of a complex field.
			String[] parts;
			if (name.endsWith("Numeric")) {
				// Special case: this is not a property alternative, but a numeric
				// alternative for a metadata field.
				// (TODO: this should probably be changed or removed)
				parts = new String[] { name };
			} else {
				parts = ComplexFieldUtil.getNameComponents(name);
			}
			if (parts.length == 1 && !complexFields.containsKey(parts[0])) {
				// Probably a metadata field (or, if using old style, the main field
				// of a complex field; if so, we'll figure that out later)
				metadataFields.put(name, getFieldType(name));
			} else {
				// Part of complex field.
				if (metadataFields.containsKey(parts[0])) {
					// This complex field was incorrectly identified as a metadata field at first.
					// Correct this now.
					if (hasOffsets(parts[0])) {
						// Must be a nameless main property. Change the setting if necessary.
						ComplexFieldUtil._setMainPropertyNameless(true);
					}
					if (!ComplexFieldUtil.isMainPropertyNameless()) {
						throw new RuntimeException(
								"Complex field and metadata field with same name, error! ("
										+ parts[0] + ")");
					}

					metadataFields.remove(parts[0]);
				}

				// Get or create descriptor object.
				ComplexFieldDesc cfd = getOrCreateComplexField(parts[0]);
				cfd.processIndexField(parts);
			}
		}

		// Detect the main properties for all complex fields
		// (looks for fields with char offset information stored)
		mainContentsField = null;
		for (ComplexFieldDesc d: complexFields.values()) {
			if (mainContentsField == null || d.getName().equals("contents"))
				mainContentsField = d;
			d.detectMainProperty(reader);
		}

	}

	/**
	 * The main contents field in our index. This is either the complex field with the name "contents",
	 * or if that doesn't exist, the first complex field found.
	 * @return the main contents field
	 */
	public ComplexFieldDesc getMainContentsField() {
		return mainContentsField;
	}

	/** Detect type by finding the first document that includes this
	 * field and inspecting the Fieldable. This assumes that the field type
	 * is the same for all documents.
	 *
	 * @param fieldName the field name to determine the type for
	 * @return type of the field (text or numeric)
	 */
	private FieldType getFieldType(String fieldName) {
		FieldType type = FieldType.TEXT;

		if (fieldName.endsWith("Numeric") || fieldName.endsWith("Num"))
			type = FieldType.NUMERIC;

		/*
		TODO: Slow. Come up with a faster alternative or a better naming convention?

		for (int n = 0; n < reader.maxDoc(); n++) {
			if (!reader.isDeleted(n)) {
				Document d;
				try {
					d = reader.document(n);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				Fieldable f = d.getFieldable(fieldName);
				if (f != null) {
					if (f instanceof NumericField)
						type = FieldType.NUMERIC;
					break;
				}
			}
		}*/

		return type;
	}

	/** See if a field has character offset information stored.
	 *
	 * @param luceneFieldName the field name to inspect
	 * @return true if it has offsets, false if not
	 */
	public boolean hasOffsets(String luceneFieldName) {
		// Iterate over documents in the index until we find a property
		// for this complex field that has stored character offsets. This is
		// our main property.
		for (int n = 0; n < reader.maxDoc(); n++) {
			if (!reader.isDeleted(n)) {
				try {
					TermFreqVector tv = reader.getTermFreqVector(n, luceneFieldName);
					if (tv == null) {
						// No term vector; probably not stored in this document.
						continue;
					}
					if (tv instanceof TermPositionVector) {
						// Check if there are offsets stored.
						return ((TermPositionVector) tv).getOffsets(0) != null;
					}
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
		return false;
	}

	private ComplexFieldDesc getOrCreateComplexField(String name) {
		ComplexFieldDesc cfd = getComplexFieldDesc(name);
		if (cfd == null) {
			cfd = new ComplexFieldDesc(name);
			complexFields.put(name, cfd);
		}
		return cfd;
	}

	/** Get the names of all the complex fields in our index
	 * @return the complex field names */
	public Collection<String> getComplexFields() {
		return complexFields.keySet();
	}

	/** Get the description of one complex field
	 * @param fieldName name of the field
	 * @return the field description */
	public ComplexFieldDesc getComplexFieldDesc(String fieldName) {
		return complexFields.get(fieldName);
	}

	/** Get the names of all the metadata fields in our index
	 * @return the names */
	public Collection<String> getMetadataFields() {
		return metadataFields.keySet();
	}

	/** Get the type of one metadata field
	 * @param fieldName name of the field
	 * @return the type of field */
	public IndexStructure.FieldType getMetadataType(String fieldName) {
		return metadataFields.get(fieldName);
	}

	public String getDocumentTitleField() {
		// Find documents with title, name or desc in the field name
		String field;
		field = findTextField("title");
		if (field == null)
			field = findTextField("name");
		if (field == null)
			field = findTextField("desc");
		if (field != null)
			return field;

		// Return the first text field we can find.
		for (Map.Entry<String, FieldType> e: metadataFields.entrySet()) {
			if (e.getValue() == FieldType.TEXT) {
				return e.getKey();
			}
		}
		return null;
	}

	public String findTextField(String search) {
		// Find documents with title in the name
		List<String> fieldsFound = new ArrayList<String>();
		for (Map.Entry<String, FieldType> e: metadataFields.entrySet()) {
			if (e.getValue() == FieldType.TEXT && e.getKey().toLowerCase().contains(search)) {
				fieldsFound.add(e.getKey());
			}
		}
		if (fieldsFound.size() == 0)
			return null;

		// Sort (so we get titleLevel1 not titleLevel2 for example)
		Collections.sort(fieldsFound);
		return fieldsFound.get(0);
	}

	public void print(PrintStream out) {
		out.println("COMPLEX FIELDS");
		for (ComplexFieldDesc cf: complexFields.values()) {
			out.println("- " + cf.getName());
			cf.print(out);
		}

		out.println("\nMETADATA FIELDS");
		String titleField = getDocumentTitleField();
		for (Map.Entry<String, FieldType> e: metadataFields.entrySet()) {
			if (e.getKey().endsWith("Numeric"))
				continue; // special case, will probably be removed later
			FieldType type = e.getValue();
			out.println("- " + e.getKey() + (type == FieldType.TEXT ? "" : " (" + type + ")")
					+ (e.getKey().equals(titleField) ? " (TITLEFIELD)" : ""));
		}
	}

}