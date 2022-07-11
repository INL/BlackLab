package nl.inl.blacklab.search;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.LeafReader;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.forwardindex.FiidLookup;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndexExternal;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.lucene.DocIntFieldGetter;
import nl.inl.blacklab.search.lucene.DocIntFieldGetterExternal;

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

    @Override
    public boolean allFilesInIndex() {
        return false;
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

}
