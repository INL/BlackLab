package nl.inl.blacklab.search.indexstructure;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.index.complex.ComplexFieldUtil.BookkeepFieldType;
import nl.inl.util.StringUtil;

import org.apache.log4j.Logger;
import org.apache.lucene.index.IndexReader;

/** Description of a complex field */
public class ComplexFieldDesc extends BaseFieldDesc {
	protected static final Logger logger = Logger.getLogger(ComplexFieldDesc.class);

	/** This complex field's properties */
	private Map<String, PropertyDesc> props;

	/** The field's main property */
	private PropertyDesc mainProperty;

	/** The field's main property name (for storing the main prop name before we have the prop. descriptions) */
	private String mainPropertyName;

	/** Does the field have an associated content store? */
	private boolean contentStore;

	/** Is the field length in tokens stored? */
	private boolean lengthInTokens;

	/** Are there XML tag locations stored for this field? */
	private boolean xmlTags;

	/** These properties should not get a forward index. */
	private Set<String> noForwardIndexProps = Collections.emptySet();

	public ComplexFieldDesc(String name) {
		super(name);
		props = new TreeMap<>();
		contentStore = false;
		lengthInTokens = false;
		xmlTags = false;
		mainProperty = null;
	}

	@Override
	public String toString() {
		return fieldName + " [" + StringUtil.join(props.values(), ", ") + "]";
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
		if (!props.containsKey(name))
			throw new IllegalArgumentException("Property '" + name + "' not found!");
		return props.get(name);
	}

	public boolean hasContentStore() {
		return contentStore;
	}

	public boolean hasLengthTokens() {
		return lengthInTokens;
	}

	/**
	 * Returns the Lucene field that contains the length (in tokens)
	 * of this field, or null if there is no such field.
	 *
	 * @return the field name or null if lengths weren't stored
	 */
	public String getTokenLengthField() {
		return lengthInTokens ? ComplexFieldUtil.lengthTokensField(fieldName) : null;
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
		if (parts.length == 1)
			throw new IllegalArgumentException("Complex field with just basename given, error!");

		String propPart = parts[1];

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
			} else {
				// Property bookkeeping field
				if (parts[3].equals(ComplexFieldUtil.FORWARD_INDEX_ID_BOOKKEEP_NAME)) {
					pd.setForwardIndex(true);
				} else
					throw new IllegalArgumentException("Unknown property bookkeeping field " + parts[3]);
			}
		}
	}

	PropertyDesc getOrCreateProperty(String name) {
		PropertyDesc pd = props.get(name);
		if (pd == null) {
			pd = new PropertyDesc(name);
			props.put(name, pd);
		}
		return pd;
	}

	public PropertyDesc getMainProperty() {
		if (mainProperty == null && mainPropertyName != null) {
			// Set during indexing, when we don't actually have property information
			// available (because the index is being built up, so we couldn't detect
			// it on startup).
			// Just create a property with the correct name.
			mainProperty = new PropertyDesc(mainPropertyName);
			props.put(mainPropertyName, mainProperty);
			mainPropertyName = null;
		}
		return mainProperty;
	}

	public void detectMainProperty(IndexReader reader) {
		if (mainPropertyName != null && mainPropertyName.length() > 0) {
			// Main property name was set from index metadata before we
			// had the property desc. available; use that now and don't do
			// any actual detecting.
			mainProperty = getPropertyDesc(mainPropertyName);
			mainPropertyName = null;
			//return;
		}

		PropertyDesc firstProperty = null;
		for (PropertyDesc pr: props.values()) {
			if (firstProperty == null)
				firstProperty = pr;
			if (pr.detectOffsetsAlternative(reader, fieldName)) {
				// This field has offsets stored. Must be the main prop field.
				if (mainProperty == null) {
					mainProperty = pr;
				} else {
					// Was already set from metadata file; same..?
					if (mainProperty != pr) {
						logger.warn("Metadata says main property for field " + getName() + " is " + mainProperty.getName() + ", but offsets are stored in " + pr.getName());
					}
				}
				return;
			}
		}

		// None have offsets; just assume the first property is the main one
		// (note that not having any offsets makes it impossible to highlight the
		// original content, but this may not be an issue. We probably need
		// a better way to keep track of the main property)
		logger.warn("No property with offsets found; assume first property (" + firstProperty.getName() + ") is main property");
		mainProperty = firstProperty;

		// throw new RuntimeException(
		// "No main property (with char. offsets) detected for complex field " + fieldName);
	}

	public void print(PrintWriter out) {
		for (PropertyDesc pr: props.values()) {
			out.println("  * Property: " + pr.toString());
		}
		out.println("  * " + (contentStore ? "Includes" : "No") + " content store");
		out.println("  * " + (xmlTags ? "Includes" : "No") + " XML tag index");
		out.println("  * " + (lengthInTokens ? "Includes" : "No") + " document length field");
	}

	public void setMainPropertyName(String mainPropertyName) {
		this.mainPropertyName = mainPropertyName;
		if (props.containsKey(mainPropertyName))
			mainProperty = props.get(mainPropertyName);
	}

	public void setNoForwardIndexProps(Set<String> noForwardIndexProps) {
		this.noForwardIndexProps = noForwardIndexProps;
	}

	public Set<String> getNoForwardIndexProps() {
		return noForwardIndexProps;
	}

}