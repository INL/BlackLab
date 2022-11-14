package nl.inl.blacklab.index.annotated;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import nl.inl.blacklab.index.BLInputDocument;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldImpl;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;

/**
 * An annotated field is like a Lucene field, but in addition to its "normal"
 * value, it can have multiple annnotations per word (not just a single token).
 * The annotations might be "headword", "pos" (part of speech), "namedentity"
 * (whether or not the word is (part of) a named entity like a location or
 * place), etc.
 *
 * Annotated fields are implemented by indexing a field in Lucene for each
 * annotation. For example, if annotated field "contents" has annotations "headword"
 * and "pos", there would be 3 Lucene fields for the annotated field: "contents",
 * "contents__headword" and "contents__pos".
 *
 * The main field ("contents" in the above example) may include offset
 * information if you want (e.g. for highlighting). All Lucene fields will
 * include position information (for use with SpanQueries).
 *
 * N.B. It is crucial that everything stays in synch, so you should call all the
 * appropriate add*() methods for each annotation and each token, or use the
 * correct position increments to keep everything synched up. The same goes for
 * addStartChar() and addEndChar() (although, if you don't want any offsets, you
 * need not call these).
 */
public class AnnotatedFieldWriter {

    protected static final Logger logger = LogManager.getLogger(AnnotatedFieldWriter.class);

    private final Map<String, AnnotationWriter> annotations = new HashMap<>();

    private IntArrayList start = new IntArrayList();

    private IntArrayList end = new IntArrayList();

    private final String fieldName;

    private final AnnotationWriter mainAnnotation;

    private final Set<String> noForwardIndexAnnotations = new HashSet<>();

    private final boolean needsPrimaryValuePayloads;

    private AnnotatedField field;

    public void setNoForwardIndexProps(Set<String> noForwardIndexAnnotations) {
        this.noForwardIndexAnnotations.clear();
        this.noForwardIndexAnnotations.addAll(noForwardIndexAnnotations);
    }

    /**
     * Construct a AnnotatedFieldWriter object with a main annotation.
     *
     * NOTE: right now, the main annotation will always have a forward index.
     * Maybe make this configurable?
     *
     * @param name field name
     * @param mainAnnotationName main annotation name (e.g. "word")
     * @param sensitivity ways to index main annotation, with respect to case- and
     *            diacritics-sensitivity.
     * @param mainPropHasPayloads does the main annotation have payloads?
     * @param needsPrimaryValuePayloads should payloads indicate whether value is primary or not?
     */
    public AnnotatedFieldWriter(String name, String mainAnnotationName, AnnotationSensitivities sensitivity,
            boolean mainPropHasPayloads, boolean needsPrimaryValuePayloads) {
        if (!AnnotatedFieldNameUtil.isValidXmlElementName(name))
            logger.warn("Field name '" + name
                    + "' is discouraged (field/annotation names should be valid XML element names)");
        if (!AnnotatedFieldNameUtil.isValidXmlElementName(mainAnnotationName))
            logger.warn("Annotation name '" + mainAnnotationName
                    + "' is discouraged (field/annotation names should be valid XML element names)");
        boolean includeOffsets = true;
        fieldName = name;
        this.needsPrimaryValuePayloads = needsPrimaryValuePayloads;
        mainAnnotation = new AnnotationWriter(this, mainAnnotationName, sensitivity, includeOffsets,
                mainPropHasPayloads, needsPrimaryValuePayloads);
        annotations.put(mainAnnotationName, mainAnnotation);
    }

    public int numberOfTokens() {
        return start.size();
    }

    public AnnotationWriter addAnnotation(String name, AnnotationSensitivities sensitivity, boolean includePayloads,
            boolean createForwardIndex) {
        if (!AnnotatedFieldNameUtil.isValidXmlElementName(name))
            logger.warn("Annotation name '" + name
                    + "' is discouraged (field/annotation names should be valid XML element names)");
        AnnotationWriter p = new AnnotationWriter(this, name, sensitivity, false, includePayloads,
                needsPrimaryValuePayloads);
        if (noForwardIndexAnnotations.contains(name) || !createForwardIndex) {
            p.setHasForwardIndex(false);
        }
        annotations.put(name, p);
        return p;
    }

    public AnnotationWriter addAnnotation(String name, AnnotationSensitivities sensitivity, boolean createForwardIndex) {
        return addAnnotation(name, sensitivity, false, createForwardIndex);
    }

    public void addStartChar(int startChar) {
        start.add(startChar);
    }

    public void addEndChar(int endChar) {
        end.add(endChar);
    }

    public void addToDoc(BLInputDocument doc) {
        for (AnnotationWriter p : annotations.values()) {
            p.addToDoc(doc, fieldName, start, end);
        }

        // Add number of tokens in annotated field as a stored field,
        // because we need to be able to find this annotation quickly
        // for SpanQueryNot.
        // (Also note that this is the actual number of words + 1,
        //  because we always store a "extra closing token" at the end
        //  that doesn't contain a word but may contain trailing punctuation)
        String lengthTokensFieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);
        int lengthTokensValue = numberOfTokens();
        doc.addStoredNumericField(lengthTokensFieldName, lengthTokensValue, true);
    }

    /**
     * Clear the internal state for reuse.
     */
    public void clear() {
        // Don't reuse buffers, reclaim memory so we don't run out
        start = new IntArrayList();
        end = new IntArrayList();

        for (AnnotationWriter p : annotations.values()) {
            p.clear();
        }
    }

    public AnnotationWriter annotation(String name) {
        return annotations.get(name);
    }

    public boolean hasAnnotation(String name) {
        return annotations.containsKey(name);
    }

    public AnnotationWriter mainAnnotation() {
        return mainAnnotation;
    }

    public AnnotationWriter tagsAnnotation() {
        AnnotationWriter rv = annotation(AnnotatedFieldNameUtil.TAGS_ANNOT_NAME);
        if (rv == null) {
            throw new IllegalArgumentException("Undefined annotation '" + AnnotatedFieldNameUtil.TAGS_ANNOT_NAME + "'");
        }
        return rv;
    }

    public AnnotationWriter punctAnnotation() {
        AnnotationWriter rv = annotation(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME);
        if (rv == null) {
            throw new IllegalArgumentException("Undefined annotation '" + AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME + "'");
        }
        return rv;
    }

    public String name() {
        return fieldName;
    }

    public Collection<AnnotationWriter> annotationWriters() {
        return annotations.values();
    }

    public void setAnnotatedField(AnnotatedField field) {
        this.field = field;
        // If the indexmetadata file specified a list of annotations that shouldn't get a forward
        // index, we need to know.
        AnnotatedFieldImpl fieldImpl = (AnnotatedFieldImpl)field;
        setNoForwardIndexProps(fieldImpl.getNoForwardIndexAnnotations());
    }

    public AnnotatedField field() {
        return field;
    }

    @Override
    public String toString() {
        return "AnnotatedFieldWriter(" + fieldName + ")";
    }

}
