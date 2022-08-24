package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndexExternalWriter;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndexAbstract;
import nl.inl.blacklab.forwardindex.ForwardIndexExternal;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorExternal;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataExternal;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
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

    @Override
    protected IndexMetadataWriter getIndexMetadata(boolean createNewIndex, ConfigInputFormat config)
            throws IndexVersionMismatch {
        return new IndexMetadataExternal(this, indexDirectory(), createNewIndex, config);
    }

    @Override
    protected IndexMetadataWriter getIndexMetadata(boolean createNewIndex, File indexTemplateFile)
            throws IndexVersionMismatch {
        return new IndexMetadataExternal(this, indexDirectory(), createNewIndex, indexTemplateFile);
    }

    public ForwardIndex createForwardIndex(AnnotatedField field) {
        return new ForwardIndexExternal(this, field);
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

    @Override
    public ForwardIndexAccessor forwardIndexAccessor(String searchField) {
        return new ForwardIndexAccessorExternal(this, annotatedField(searchField));
    }

    @Override
    public Query getAllRealDocsQuery() {
        return new MatchAllDocsQuery(); // there are no non-real documents in this index type
    }
}
