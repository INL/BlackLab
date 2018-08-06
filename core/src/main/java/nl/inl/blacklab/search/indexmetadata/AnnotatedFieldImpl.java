package nl.inl.blacklab.search.indexmetadata;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.index.complex.ComplexFieldUtil.BookkeepFieldType;
import nl.inl.blacklab.search.indexmetadata.nint.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.nint.Annotation;
import nl.inl.blacklab.search.indexmetadata.nint.Annotations;
import nl.inl.blacklab.search.indexmetadata.nint.Freezable;
import nl.inl.blacklab.search.indexmetadata.nint.MatchSensitivity;
import nl.inl.util.StringUtil;

/** Description of a complex field */
public class AnnotatedFieldImpl extends FieldImpl implements AnnotatedField, Freezable {
    
    private final class AnnotationsImpl implements Annotations {
        @Override
        public Annotation main() {
            if (mainProperty == null && mainPropertyName != null) {
                // Set during indexing, when we don't actually have property information
                // available (because the index is being built up, so we couldn't detect
                // it on startup).
                // Just create a property with the correct name, or retrieve it if it
                // was defined in the indexmetadata.
                mainProperty = getOrCreateProperty(mainPropertyName);
                mainPropertyName = null;
            }
            return mainProperty;
        }

        @Override
        public Iterator<Annotation> iterator() {
            Iterator<AnnotationImpl> it = annotationsDisplayOrder.iterator();
            return new Iterator<Annotation>() {
                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public Annotation next() {
                    return it.next();
                }
                
            };
        }

        @Override
        public Stream<Annotation> stream() {
            return annotationsDisplayOrder.stream().map(a -> (Annotation)a);
        }

        @Override
        public Annotation get(String name) {
            return props.get(name);
        }

        @Override
        public boolean exists(String name) {
            return props.containsKey(name);
        }
    }

    protected static final Logger logger = LogManager.getLogger(AnnotatedFieldImpl.class);

    /** This complex field's properties, sorted by name */
    private Map<String, AnnotationImpl> props;
    
    /** This field's annotations, in desired display order */
    private List<AnnotationImpl> annotationsDisplayOrder;

    /** The field's main property */
    private AnnotationImpl mainProperty;

    /**
     * The field's main property name (for storing the main prop name before we have
     * the prop. descriptions)
     */
    private String mainPropertyName;

    /** Is the field length in tokens stored? */
    private boolean lengthInTokens;

    /** Are there XML tag locations stored for this field? */
    private boolean xmlTags;

    /** These properties should not get a forward index. */
    private Set<String> noForwardIndexProps = Collections.emptySet();

    /** Annotation display order. If not specified, use reasonable defaults. */
    private List<String> displayOrder = new ArrayList<>(Arrays.asList("word", "lemma", "pos"));

    /** Compares annotation names by displayOrder. */
    private Comparator<AnnotationImpl> annotationOrderComparator;

    private boolean frozen;

    private AnnotationsImpl annotationsImpl;

    AnnotatedFieldImpl(String name) {
        super(name);
        props = new TreeMap<String, AnnotationImpl>();
        annotationsDisplayOrder = new ArrayList<>();
        annotationOrderComparator = new Comparator<AnnotationImpl>() {
            @Override
            public int compare(AnnotationImpl a, AnnotationImpl b) {
                int ai = displayOrder.indexOf(a.name());
                if (ai < 0)
                    ai = Integer.MAX_VALUE;
                int bi = displayOrder.indexOf(b.name());
                if (bi < 0)
                    bi = Integer.MAX_VALUE;
                return ai == bi ? 0 : (ai > bi ? 1 : -1);
            }
        };
        
        contentStore = false;
        lengthInTokens = false;
        xmlTags = false;
        mainProperty = null;
        annotationsImpl = new AnnotationsImpl();
    }

    @Override
    public String toString() {
        return fieldName + " [" + StringUtil.join(props.values(), ", ") + "]";
    }
    
    @Override
    public Annotations annotations() {
        return annotationsImpl;
    }

    @Override
    public boolean hasLengthTokens() {
        return lengthInTokens;
    }

    /**
     * Returns the Lucene field that contains the length (in tokens) of this field,
     * or null if there is no such field.
     *
     * @return the field name or null if lengths weren't stored
     */
    @Override
    public String tokenLengthField() {
        return lengthInTokens ? ComplexFieldUtil.lengthTokensField(fieldName) : null;
    }

    @Override
    public boolean hasXmlTags() {
        return xmlTags;
    }

    /**
     * Checks if this field has a "punctuation" forward index, storing all the
     * intra-word characters (whitespace and punctuation) so we can build
     * concordances directly from the forward indices.
     * 
     * @return true iff there's a punctuation forward index.
     */
    @Override
    public boolean hasPunctuationForwardIndex() {
        AnnotationImpl pd = props.get(ComplexFieldUtil.PUNCTUATION_PROP_NAME);
        return pd != null && pd.hasForwardIndex();
    }

    void print(PrintWriter out) {
        for (Annotation pr : props.values()) {
            out.println("  * Property: " + pr.toString());
        }
        out.println("  * " + (contentStore ? "Includes" : "No") + " content store");
        out.println("  * " + (xmlTags ? "Includes" : "No") + " XML tag index");
        out.println("  * " + (lengthInTokens ? "Includes" : "No") + " document length field");
    }

    // (public because used in ComplexField while indexing) 
    public Set<String> getNoForwardIndexProps() {
        return noForwardIndexProps;
    }

    List<String> getDisplayOrder() {
        return Collections.unmodifiableList(displayOrder);
    }
    
    // Methods that mutate data
    // ------------------------------------------------------
    
    /**
     * An index field was found and split into parts, and belongs to this complex
     * field. See what type it is and update our fields accordingly.
     * 
     * @param parts parts of the Lucene index field name
     */
    void processIndexField(String[] parts) {
        ensureNotFrozen();
    
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
        AnnotationImpl pd = getOrCreateProperty(propPart);
        if (pd.name().equals(ComplexFieldUtil.START_TAG_PROP_NAME))
            xmlTags = true;
        if (parts.length > 2) {
            if (parts[2] != null) {
                // Alternative
                pd.addAlternative(MatchSensitivity.fromLuceneFieldCode(parts[2]));
            } else {
                // Property bookkeeping field
                if (parts[3].equals(ComplexFieldUtil.FORWARD_INDEX_ID_BOOKKEEP_NAME)) {
                    pd.setForwardIndex(true);
                } else
                    throw new IllegalArgumentException("Unknown property bookkeeping field " + parts[3]);
            }
        }
    }

    AnnotationImpl getOrCreateProperty(String name) {
        ensureNotFrozen();
        AnnotationImpl pd = props.get(name);
        if (pd == null) {
            pd = new AnnotationImpl(this, name);
            putProperty(pd);
        }
        return pd;
    }

    void putProperty(AnnotationImpl propDesc) {
        ensureNotFrozen();
        props.put(propDesc.name(), propDesc);
        annotationsDisplayOrder.add(propDesc);
        annotationsDisplayOrder.sort(annotationOrderComparator);
    }

    void detectMainProperty(IndexReader reader) {
        ensureNotFrozen();
        if (mainPropertyName != null && mainPropertyName.length() > 0) {
            // Main property name was set from index metadata before we
            // had the property desc. available; use that now and don't do
            // any actual detecting.
            if (!props.containsKey(mainPropertyName))
                throw new IllegalArgumentException("Main property '" + mainPropertyName + "' (from index metadata) not found!");
            mainProperty = props.get(mainPropertyName);
            mainPropertyName = null;
            //return;
        }
    
        AnnotationImpl firstProperty = null;
        for (AnnotationImpl pr : props.values()) {
            if (firstProperty == null)
                firstProperty = pr;
            if (pr.detectOffsetsAlternative(reader, fieldName)) {
                // This field has offsets stored. Must be the main prop field.
                if (mainProperty == null) {
                    mainProperty = pr;
                } else {
                    // Was already set from metadata file; same..?
                    if (mainProperty != pr) {
                        logger.warn("Metadata says main property for field " + name() + " is "
                                + mainProperty.name() + ", but offsets are stored in " + pr.name());
                    }
                }
                return;
            }
        }
    
        // None have offsets; just assume the first property is the main one
        // (note that not having any offsets makes it impossible to highlight the
        // original content, but this may not be an issue. We probably need
        // a better way to keep track of the main property)
        logger.warn("No property with offsets found; assume first property (" + firstProperty.name()
                + ") is main property");
        mainProperty = firstProperty;
    
        // throw new RuntimeException(
        // "No main property (with char. offsets) detected for complex field " + fieldName);
    }

    void setMainPropertyName(String mainPropertyName) {
        ensureNotFrozen();
        this.mainPropertyName = mainPropertyName;
        if (props.containsKey(mainPropertyName))
            mainProperty = props.get(mainPropertyName);
    }

    void setNoForwardIndexProps(Set<String> noForwardIndexProps) {
        ensureNotFrozen();
        this.noForwardIndexProps = noForwardIndexProps;
    }

    void setDisplayOrder(List<String> displayOrder) {
        ensureNotFrozen();
        this.displayOrder.clear();
        this.displayOrder.addAll(displayOrder);
        this.annotationsDisplayOrder.sort(annotationOrderComparator);
    }

    @Override
    public void freeze() {
        this.frozen = true;
        this.props.values().forEach(annotation -> annotation.freeze());
    }
    
    @Override
    public boolean isFrozen() {
        return this.frozen;
    }

}
