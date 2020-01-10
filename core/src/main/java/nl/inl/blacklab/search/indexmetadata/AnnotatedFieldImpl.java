package nl.inl.blacklab.search.indexmetadata;

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
import org.apache.lucene.index.DocValuesType;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil.BookkeepFieldType;

/** An annotated field */
public class AnnotatedFieldImpl extends FieldImpl implements AnnotatedField, Freezable<AnnotatedFieldImpl> {
    
    public final class AnnotationsImpl implements Annotations {
        @Override
        public Annotation main() {
            if (mainAnnotation == null && mainAnnotationName != null) {
                // Set during indexing, when we don't actually have annotation information
                // available (because the index is being built up, so we couldn't detect
                // it on startup).
                // Just create an annotation with the correct name, or retrieve it if it
                // was defined in the indexmetadata.
                mainAnnotation = annots.get(mainAnnotationName);
                mainAnnotationName = null;
            }
            return mainAnnotation;
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
            return annots.get(name);
        }

        @Override
        public boolean exists(String name) {
            return annots.containsKey(name);
        }

        @Override
        public boolean isEmpty() {
            return annots.isEmpty();
        }
    }

    protected static final Logger logger = LogManager.getLogger(AnnotatedFieldImpl.class);

    private IndexMetadata indexMetadata;
    
    /** This field's annotations, sorted by name */
    private Map<String, AnnotationImpl> annots;
    
    /** This field's annotations, in desired display order */
    private List<AnnotationImpl> annotationsDisplayOrder;

    /** The field's main annotation */
    private AnnotationImpl mainAnnotation;

    /**
     * The field's main annotation name (for storing the main annot name before we have
     * the annot. descriptions)
     */
    private String mainAnnotationName;

    /** Is the field length in tokens stored? */
    private boolean lengthInTokens;

    /** Does the length field contain DocValues? */
    private DocValuesType lengthInTokensDocValuesType = DocValuesType.NONE;

    /** Are there XML tag locations stored for this field? */
    private boolean xmlTags;

    /** These annotations should not get a forward index. */
    private Set<String> noForwardIndexAnnotations = Collections.emptySet();

    /** Annotation display order. If not specified, use reasonable defaults. */
    private List<String> displayOrder = new ArrayList<>(Arrays.asList("word", "lemma", "pos"));

    /** Compares annotation names by displayOrder. */
    private Comparator<AnnotationImpl> annotationOrderComparator;

    private boolean frozen;

    private AnnotationsImpl annotationsImpl;

    AnnotatedFieldImpl(IndexMetadata indexMetadata, String name) {
        super(name);
        this.indexMetadata = indexMetadata;
        annots = new TreeMap<>();
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
        mainAnnotation = null;
        annotationsImpl = new AnnotationsImpl();
    }

    @Override
    public String toString() {
        return fieldName;
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
        return lengthInTokens ? AnnotatedFieldNameUtil.lengthTokensField(fieldName) : null;
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
        AnnotationImpl pd = annots.get(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME);
        return pd != null && pd.hasForwardIndex();
    }

    // (public because used in AnnotatedFieldWriter while indexing) 
    public Set<String> getNoForwardIndexAnnotations() {
        return noForwardIndexAnnotations;
    }

    List<String> getDisplayOrder() {
        return Collections.unmodifiableList(displayOrder);
    }
    
    // Methods that mutate data
    // ------------------------------------------------------
    
    /**
     * An index field was found and split into parts, and belongs to this annotated
     * field. See what type it is and update our fields accordingly.
     * 
     * @param parts parts of the Lucene index field name
     * @param fi the field's FieldInfo structure 
     */
    synchronized void processIndexField(String[] parts, FieldInfo fi) {
        ensureNotFrozen();
    
        // See if this is a builtin bookkeeping field or a annotation.
        if (parts.length == 1)
            throw new IllegalArgumentException("Annotated field with just basename given, error!");
    
        String annotPart = parts[1];
    
        if (annotPart == null && parts.length >= 3) {
            // Bookkeeping field
            BookkeepFieldType bookkeepingFieldIndex = AnnotatedFieldNameUtil
                    .whichBookkeepingSubfield(parts[3]);
            switch (bookkeepingFieldIndex) {
            case CONTENT_ID:
                // Annotated field has content store
                contentStore = true;
                return;
            case FORWARD_INDEX_ID:
                // Main annotation has forward index
                getOrCreateAnnotation("").setForwardIndex(true);
                return;
            case LENGTH_TOKENS:
                // Annotated field has length in tokens
                lengthInTokens = true;
                lengthInTokensDocValuesType = fi.getDocValuesType();
                return;
            }
            throw new BlackLabRuntimeException();
        }
    
        // Not a bookkeeping field; must be a annotation (alternative).
        AnnotationImpl pd = getOrCreateAnnotation(annotPart);
        if (pd.name().equals(AnnotatedFieldNameUtil.TAGS_ANNOT_NAME))
            xmlTags = true;
        if (parts.length > 2) {
            if (parts[2] != null) {
                // Alternative
                pd.addAlternative(MatchSensitivity.fromLuceneFieldSuffix(parts[2]));
            } else {
                // Annotation bookkeeping field
                if (parts[3].equals(AnnotatedFieldNameUtil.FORWARD_INDEX_ID_BOOKKEEP_NAME)) {
                    pd.setForwardIndex(true);
                } else
                    throw new IllegalArgumentException("Unknown annotation bookkeeping field " + parts[3]);
            }
        }
    }

    public synchronized AnnotationImpl getOrCreateAnnotation(String name) {
        ensureNotFrozen();
        AnnotationImpl pd = annots.get(name);
        if (pd == null) {
            pd = new AnnotationImpl(indexMetadata, this, name);
            putAnnotation(pd);
        }
        return pd;
    }

    synchronized void putAnnotation(AnnotationImpl annotDesc) {
        ensureNotFrozen();
        annots.put(annotDesc.name(), annotDesc);
        annotationsDisplayOrder.add(annotDesc);
        annotationsDisplayOrder.sort(annotationOrderComparator);
    }

    synchronized void detectMainAnnotation(IndexReader reader) {
        ensureNotFrozen();
        if (mainAnnotationName != null && mainAnnotationName.length() > 0) {
            // Main annotation name was set from index metadata before we
            // had the annotation desc. available; use that now and don't do
            // any actual detecting.
            if (!annots.containsKey(mainAnnotationName))
                throw new IllegalArgumentException("Main annotation '" + mainAnnotationName + "' (from index metadata) not found!");
            mainAnnotation = annots.get(mainAnnotationName);
            mainAnnotationName = null;
            //return;
        }
        
        if (annots.isEmpty())
            return; // dummy field for storing linked documents; has no annotations
    
        AnnotationImpl firstAnnotation = null;
        for (AnnotationImpl pr : annots.values()) {
            if (firstAnnotation == null)
                firstAnnotation = pr;
            if (pr.detectOffsetsSensitivity(reader, fieldName)) {
                // This field has offsets stored. Must be the main annotation field.
                if (mainAnnotation == null) {
                    mainAnnotation = pr;
                } else {
                    // Was already set from metadata file; same..?
                    if (mainAnnotation != pr) {
                        logger.warn("Metadata says main annotation for field " + name() + " is "
                                + mainAnnotation.name() + ", but offsets are stored in " + pr.name());
                    }
                }
                return;
            }
        }
    
        // None have offsets; just assume the first annotation is the main one
        // (note that not having any offsets makes it impossible to highlight the
        // original content, but this may not be an issue. We probably need
        // a better way to keep track of the main annotation)
        if (firstAnnotation != null) {
            logger.warn("No annotation with offsets found; assume first annotation (" + firstAnnotation.name()
                    + ") is main annotation");
            mainAnnotation = firstAnnotation;
        }
    }

    synchronized void setMainAnnotationName(String mainAnnotationName) {
        ensureNotFrozen();
        this.mainAnnotationName = mainAnnotationName;
        if (annots.containsKey(mainAnnotationName))
            mainAnnotation = annots.get(mainAnnotationName);
    }

    void setNoForwardIndexAnnotations(Set<String> noForwardIndexAnnotations) {
        ensureNotFrozen();
        this.noForwardIndexAnnotations = noForwardIndexAnnotations;
    }

    synchronized void setDisplayOrder(List<String> displayOrder) {
        ensureNotFrozen();
        this.displayOrder.clear();
        this.displayOrder.addAll(displayOrder);
        this.annotationsDisplayOrder.sort(annotationOrderComparator);
    }

    @Override
    synchronized public AnnotatedFieldImpl freeze() {
        this.frozen = true;
        this.annots.values().forEach(annotation -> annotation.freeze());
        return this;
    }
    
    @Override
    public boolean isFrozen() {
        return this.frozen;
    }

    @Override
    public String offsetsField() {
        AnnotationSensitivity offsetsSensitivity = mainAnnotation.offsetsSensitivity();
        return offsetsSensitivity == null ? null : offsetsSensitivity.luceneField();
    }

    @Override
    public boolean hasTokenLengthDocValues() {
        return lengthInTokensDocValuesType != DocValuesType.NONE;
    }

}
