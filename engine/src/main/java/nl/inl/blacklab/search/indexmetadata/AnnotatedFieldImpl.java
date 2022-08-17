package nl.inl.blacklab.search.indexmetadata;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlTransient;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.IndexReader;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil.BookkeepFieldType;

/** An annotated field */
@XmlAccessorType(XmlAccessType.FIELD)
@JsonPropertyOrder({ "custom", "mainAnnotation", "hasContentStore", "hasXmlTags", "annotations" })
public class AnnotatedFieldImpl extends FieldImpl implements AnnotatedField, Freezable {

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
            return stream().iterator();
        }

        @Override
        public Stream<Annotation> stream() {
            return annots.values().stream().map(a -> a);
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
    
    /** This field's annotations */
    private final Map<String, AnnotationImpl> annots = new LinkedHashMap<>();

    /** The field's main annotation */
    @XmlTransient
    private AnnotationImpl mainAnnotation = null;

    /**
     * The field's main annotation name (for storing the main annot name before we have
     * the annot. descriptions)
     */
    @JsonProperty("mainAnnotation")
    private String mainAnnotationName;

    /** Are there XML tag locations stored for this field? */
    @JsonProperty("hasXmlTags")
    private boolean xmlTags;

    /** These annotations should not get a forward index. */
    @XmlTransient
    private Set<String> noForwardIndexAnnotations = Collections.emptySet();

    @XmlTransient
    private boolean frozen;

    @XmlTransient
    private final AnnotationsImpl annotations = new AnnotationsImpl();

    // For JAXB deserialization
    @SuppressWarnings("unused")
    AnnotatedFieldImpl() {
    }

    AnnotatedFieldImpl(String name) {
        super(name);
    }

    @Override
    public String toString() {
        return fieldName;
    }
    
    @Override
    public Annotations annotations() {
        return annotations;
    }

    /**
     * Returns the Lucene field that contains the length (in tokens) of this field,
     * or null if there is no such field.
     *
     * @return the field name or null if lengths weren't stored
     */
    @Override
    public String tokenLengthField() {
        return AnnotatedFieldNameUtil.lengthTokensField(fieldName);
    }

    @Override
    public boolean hasXmlTags() {
        return xmlTags;
    }

    // (public because used in AnnotatedFieldWriter while indexing)
    public Set<String> getNoForwardIndexAnnotations() {
        return noForwardIndexAnnotations;
    }

    /**
     * @deprecated use {@link #custom()} and .get("displayOrder", Collections.emptyList()) instead
     */
    List<String> getDisplayOrder() {
        return custom().get("displayOrder", Collections.emptyList());
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
    
        String annotName = parts[1];
    
        if (annotName == null && parts.length >= 3) {
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
                // [should never happen anymore, because main annotation always has a name now]
                throw new IllegalStateException("Found lucene field " + fi.name + " with forward index id, but no annotation name!");
            case LENGTH_TOKENS:
                // Annotated field always has length in tokens
                return;
            }
            throw new BlackLabRuntimeException();
        }
    
        // Not a bookkeeping field; must be a annotation (alternative).
        AnnotationImpl annotation = getOrCreateAnnotation(annotName);
        if (annotation.name().equals(AnnotatedFieldNameUtil.TAGS_ANNOT_NAME))
            xmlTags = true;
        if (parts.length > 2) {
            if (parts[2] != null) {
                // Sensitivity alternative for this annotation
                MatchSensitivity sensitivity = MatchSensitivity.fromLuceneFieldSuffix(parts[2]);
                annotation.addAlternative(sensitivity);
            } else {
                // Annotation bookkeeping field
                if (parts[3].equals(AnnotatedFieldNameUtil.FORWARD_INDEX_ID_BOOKKEEP_NAME)) {
                    annotation.setForwardIndex(true);
                } else
                    throw new IllegalArgumentException("Unknown annotation bookkeeping field " + parts[3]);
            }
        }
    }

    public synchronized AnnotationImpl getOrCreateAnnotation(String name) {
        AnnotationImpl pd = annots.get(name);
        if (pd == null) {
            ensureNotFrozen();
            pd = new AnnotationImpl(this, name);
            putAnnotation(pd);
            if (name.equals(AnnotatedFieldNameUtil.TAGS_ANNOT_NAME))
                xmlTags = true;
        }
        return pd;
    }

    synchronized void putAnnotation(AnnotationImpl annotDesc) {
        ensureNotFrozen();
        annots.put(annotDesc.name(), annotDesc);
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
            if (pr.detectOffsetsSensitivity(reader)) {
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
        logger.warn("No annotation with offsets found; assume first annotation (" + firstAnnotation.name()
                + ") is main annotation");
        mainAnnotation = firstAnnotation;
    }

    synchronized void setMainAnnotationName(String mainAnnotationName) {
        if (this.mainAnnotationName == null || !this.mainAnnotationName.equals(mainAnnotationName)) {
            ensureNotFrozen();
            this.mainAnnotationName = mainAnnotationName;
            if (annots.containsKey(mainAnnotationName))
                mainAnnotation = annots.get(mainAnnotationName);
        }
    }

    void setNoForwardIndexAnnotations(Set<String> noForwardIndexAnnotations) {
        ensureNotFrozen();
        this.noForwardIndexAnnotations = noForwardIndexAnnotations;
    }

    @Deprecated
    synchronized void setDisplayOrder(List<String> displayOrder) {
        ensureNotFrozen();
        custom.put("displayOrder", displayOrder);
    }

    @Override
    synchronized public void freeze() {
        this.frozen = true;
        this.annots.values().forEach(AnnotationImpl::freeze);
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

    public void fixAfterDeserialization(BlackLabIndex index, String fieldName) {
        super.fixAfterDeserialization(index, fieldName);
        for (Map.Entry<String, AnnotationImpl> entry : annots.entrySet()) {
            entry.getValue().fixAfterDeserialization(index, this, entry.getKey());
        }
    }

}
