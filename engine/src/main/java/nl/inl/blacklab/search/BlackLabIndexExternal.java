package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReader;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndexExternalWriter;
import nl.inl.blacklab.forwardindex.FiidLookup;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndexAbstract;
import nl.inl.blacklab.forwardindex.ForwardIndexExternal;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.DocIntFieldGetter;
import nl.inl.blacklab.search.lucene.DocIntFieldGetterExternal;
import nl.inl.util.VersionFile;

/**
 * A legacy BlackLab index with external forward index, etc.
 */
public class BlackLabIndexExternal extends BlackLabIndexAbstract {

    BlackLabIndexExternal(BlackLabEngine blackLab, File indexDir, boolean indexMode, boolean createNewIndex,
            ConfigInputFormat config) throws ErrorOpeningIndex {
        super(blackLab, indexDir, indexMode, createNewIndex, config);
    }

    BlackLabIndexExternal(BlackLabEngine blackLab, File indexDir, boolean indexMode, boolean createNewIndex,
            File indexTemplateFile) throws ErrorOpeningIndex {
        super(blackLab, indexDir, indexMode, createNewIndex, indexTemplateFile);
    }

    public ForwardIndex createForwardIndex(AnnotatedField field) {
        return new ForwardIndexExternal(this, field);
    }

    public DocIntFieldGetter createFiidGetter(LeafReader reader, Annotation annotation) {
        return new DocIntFieldGetterExternal(reader, annotation.forwardIndexIdField());
    }

    /**
     * Get FiidLookups for the specified annotations.
     *
     * If any of the entries in the list is null, a corresponding null will be added
     * to the result list, so the indexes of the result list will match the indexes
     * of the input list.
     *
     * @param annotations annotations to get FiidLookup for
     * @param enableRandomAccess if true, random access will be enabled for the returned objects
     * @return FiidLookup objects for the specfied annotations
     */
    public List<FiidLookup> getFiidLookups(List<Annotation> annotations, boolean enableRandomAccess) {
        if (annotations == null)
            return null; // HitPoperty.needsContext() can return null
        List<FiidLookup> fiidLookups = new ArrayList<>();
        // Legacy index with external files; must look up fiid.
        for (Annotation annotation: annotations) {
            fiidLookups.add(annotation == null ? null : FiidLookup.external(reader(), annotation, enableRandomAccess));
        }
        return fiidLookups;
    }

    /**
     * We want to call getFiid() with a Document. Add the fields we'll need.
     *
     * Use this to make sure the required fields will be loaded when retrieving
     * the Lucene Document.
     *
     * May or may not add any fields, depending on the index format.
     *
     * @param annotations annotations we want to access forward index for
     * @param fieldsToLoad (out) required fields will be added here
     */
    public void prepareForGetFiidCall(List<Annotation> annotations, Set<String> fieldsToLoad) {
        // Only needed for external forward index, because we need to look up fiid
        final List<String> annotationFINames = annotations.stream()
                .map(Annotation::forwardIndexIdField)
                .collect(Collectors.toList());
        fieldsToLoad.addAll(annotationFINames);
    }

    /**
     * Given the Lucene docId, return the forward index id.
     *
     * If all files are contained in the index, the docId and forward
     * index id are the same.
     *
     * @param annotation annotation to get the fiid for
     * @param docId Lucene doc id
     * @param doc Lucene document if available, or null otherwise
     * @return the forward index id
     */
    public int getFiid(Annotation annotation, int docId, Document doc) {
        // fiid is stored in a lucene field
        final String annotationFIName = annotation.forwardIndexIdField();
        if (doc == null)
            doc = this.luceneDoc(docId);
        return doc.getField(annotationFIName).numericValue().intValue();
    }

    protected void deleteFromForwardIndices(Document d) {
        // Delete this document in all forward indices
        for (Map.Entry<AnnotatedField, ForwardIndex> e: forwardIndices.entrySet()) {
            AnnotatedField field = e.getKey();
            ForwardIndex fi = e.getValue();
            for (Annotation annotation: field.annotations()) {
                if (annotation.hasForwardIndex())
                    ((AnnotationForwardIndexExternalWriter)fi.get(annotation)).deleteDocumentByLuceneDoc(d);
            }
        }
    }

    protected void checkCanOpenIndex(boolean indexMode, boolean createNewIndex) throws IllegalArgumentException {
        // If there's a version file (non-integrated index), check it now.
        File indexLocation = indexDirectory();
        if (!createNewIndex) {
            if (!indexMode || VersionFile.exists(indexLocation)) {
                if (!BlackLabIndex.isIndex(indexLocation)) {
                    throw new IllegalArgumentException("Not a BlackLab index, or wrong version! "
                            + VersionFile.report(indexLocation));
                }
            }
        }
    }

    @Override
    protected IndexWriter openIndexWriter(File indexDir, boolean create, Analyzer useAnalyzer) throws IOException {
        IndexWriter writer = super.openIndexWriter(indexDir, create, useAnalyzer);

        // Create or check for the version file
        if (create) {
            // New index. Create our version file.
            VersionFile.write(indexDir, "blacklab", "2");
        } else {
            // Existing index. Should already have a version file.
            if (!BlackLabIndex.isIndex(indexDir)) {
                throw new IllegalArgumentException("Not a BlackLab index, or wrong type or version! "
                        + VersionFile.report(indexDir) + ": " + indexDir);
            }
        }
        return writer;
    }

    @Override
    public void close() {
        super.close();

        // Close the (external) forward indices
        for (ForwardIndex fi: forwardIndices.values()) {
            ((ForwardIndexAbstract)fi).close();
        }
    }
}
