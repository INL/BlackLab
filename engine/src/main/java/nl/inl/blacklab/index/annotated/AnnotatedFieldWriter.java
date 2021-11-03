/*******************************************************************************
 * Copyright (c) 2010, 2012 Institute for Dutch Lexicology
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package nl.inl.blacklab.index.annotated;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.NumericDocValuesField;
import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;

import nl.inl.blacklab.index.annotated.AnnotationWriter.SensitivitySetting;
import nl.inl.blacklab.indexers.config.ConfigAnnotation;
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

    private Map<String, AnnotationWriter> annotations = new HashMap<>();

    private IntArrayList start = new IntArrayList();

    private IntArrayList end = new IntArrayList();

    private String fieldName;

    private AnnotationWriter mainAnnotation;

    private Set<String> noForwardIndexAnnotations = new HashSet<>();

    private AnnotatedField field;

    public void setNoForwardIndexProps(Set<String> noForwardIndexAnnotations) {
        this.noForwardIndexAnnotations.clear();
        this.noForwardIndexAnnotations.addAll(noForwardIndexAnnotations);
    }

    /**
     * Construct a AnnotatedFieldWriter object with a main annotation
     *
     * @param name field name
     * @param mainAnnotationName main annotation name (e.g. "word")
     * @param sensitivity ways to index main annotation, with respect to case- and
     *            diacritics-sensitivity.
     * @param mainPropHasPayloads does the main annotation have payloads?
     */
    public AnnotatedFieldWriter(String name, String mainAnnotationName, SensitivitySetting sensitivity,
            boolean mainPropHasPayloads) {
        if (!AnnotatedFieldNameUtil.isValidXmlElementName(name))
            logger.warn("Field name '" + name
                    + "' is discouraged (field/annotation names should be valid XML element names)");
        if (!AnnotatedFieldNameUtil.isValidXmlElementName(mainAnnotationName))
            logger.warn("Annotation name '" + mainAnnotationName
                    + "' is discouraged (field/annotation names should be valid XML element names)");
        boolean includeOffsets = true;
        fieldName = name;
        if (mainAnnotationName == null)
            mainAnnotationName = AnnotatedFieldNameUtil.getDefaultMainAnnotationName();
        mainAnnotation = new AnnotationWriter(this, mainAnnotationName, sensitivity, includeOffsets, mainPropHasPayloads);
        annotations.put(mainAnnotationName, mainAnnotation);
    }

    public int numberOfTokens() {
        return start.size();
    }

    public AnnotationWriter addAnnotation(ConfigAnnotation annot, String name, SensitivitySetting sensitivity, boolean includePayloads) {
        if (!AnnotatedFieldNameUtil.isValidXmlElementName(name))
            logger.warn("Annotation name '" + name
                    + "' is discouraged (field/annotation names should be valid XML element names)");
        AnnotationWriter p = new AnnotationWriter(this, name, sensitivity, false, includePayloads);
        if (noForwardIndexAnnotations.contains(name) || annot != null && !annot.createForwardIndex()) {
            p.setHasForwardIndex(false);
        }
        annotations.put(name, p);
        return p;
    }

    public AnnotationWriter addAnnotation(ConfigAnnotation annot, String name, SensitivitySetting sensitivity) {
        return addAnnotation(annot, name, sensitivity, false);
    }

    public void addStartChar(int startChar) {
        start.add(startChar);
    }

    public void addEndChar(int endChar) {
        end.add(endChar);
    }

    public void addToLuceneDoc(Document doc) {
        for (AnnotationWriter p : annotations.values()) {
            p.addToLuceneDoc(doc, fieldName, start, end);
        }

        // Add number of tokens in annotated field as a stored field,
        // because we need to be able to find this annotation quickly
        // for SpanQueryNot.
        // (Also note that this is the actual number of words + 1,
        //  because we always store a "extra closing token" at the end
        //  that doesn't contain a word but may contain trailing punctuation)
        String lengthTokensFieldName = AnnotatedFieldNameUtil.lengthTokensField(fieldName);
        int lengthTokensValue = numberOfTokens();
        doc.add(new IntField(lengthTokensFieldName, lengthTokensValue, Field.Store.YES));
        doc.add(new NumericDocValuesField(lengthTokensFieldName, lengthTokensValue)); // docvalues for fast retrieval
    }

    /**
     * Clear the internal state for reuse.
     *
     * @param reuseBuffers IMPORTANT: reuseBuffers should not be used if any
     *            document passed to {@link AnnotatedFieldWriter#addToLuceneDoc(Document)}
     *            has not been added to the IndexWriter yet. (though
     *            IndexWriter::commit is not required). Document does not copy data
     *            until it as added, so clearing our internal buffers before adding
     *            it to the writer would also remove this data from the lucene
     *            Document.
     */
    public void clear(boolean reuseBuffers) {
        // Don't reuse buffers, reclaim memory so we don't run out
//        if (reuseBuffers) {
//            start.clear();
//            end.clear();
//        } else {
            start = new IntArrayList();
            end = new IntArrayList();
//        }

        for (AnnotationWriter p : annotations.values()) {
            p.clear(reuseBuffers);
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
