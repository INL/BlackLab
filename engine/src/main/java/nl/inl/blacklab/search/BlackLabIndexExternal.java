package nl.inl.blacklab.search;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.Bits;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.contentstore.ContentStoreExternal;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndexExternalWriter;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndexAbstract;
import nl.inl.blacklab.forwardindex.ForwardIndexExternal;
import nl.inl.blacklab.index.BLIndexWriterProxyLucene;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorExternal;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.search.indexmetadata.Field;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataExternal;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;
import nl.inl.blacklab.search.lucene.BLSpanQuery;
import nl.inl.blacklab.search.lucene.SpanQueryTagsExternal;
import nl.inl.blacklab.search.results.QueryInfo;
import nl.inl.util.VersionFile;

/**
 * A legacy BlackLab index with external forward index, etc.
 */
public class BlackLabIndexExternal extends BlackLabIndexAbstract {

    BlackLabIndexExternal(BlackLabEngine blackLab, File indexDir, boolean indexMode, boolean createNewIndex,
            ConfigInputFormat config) throws ErrorOpeningIndex {
        super(indexDir.getName(), blackLab, null, indexDir, indexMode, createNewIndex, config, null);
    }

    BlackLabIndexExternal(BlackLabEngine blackLab, File indexDir, boolean indexMode, boolean createNewIndex,
            File indexTemplateFile) throws ErrorOpeningIndex {
        super(indexDir.getName(), blackLab, null, indexDir, indexMode, createNewIndex, null, indexTemplateFile);
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

    @Override
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

    @Override
    protected void checkCanOpenIndex(boolean createNewIndex) throws IllegalArgumentException {
        // If there's a version file (non-integrated index), check it now.
        File indexLocation = indexDirectory();
        if (!createNewIndex) {
            if (!indexMode() || VersionFile.exists(indexLocation)) {
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
    protected void openContentStore(Field field, boolean createNewContentStore, File indexDir) {
        // Classic external index format. Open external content store.
        File dir = new File(indexDir, "cs_" + field.name());
        if (!dir.exists() && !createNewContentStore) {
            throw new IllegalStateException("Field " + field.name() +
                    " should have content store, but directory " + dir + " not found!");
        }
        if (traceIndexOpening())
            logger.debug("    " + dir + "...");
        ContentStore cs = ContentStoreExternal.open(dir, indexMode(), createNewContentStore);
        registerContentStore(field, cs);
    }

    @Override
    public void close() {
        // Close the (external) forward indices
        for (ForwardIndex fi: forwardIndices.values()) {
            ((ForwardIndexAbstract)fi).close();
        }

        // Note that we call super.close() AFTER closing the forward indexes, or our index will momentarily
        // seem to be "finished" will actually being in an invalid state.
        super.close();
    }

    @Override
    public ForwardIndexAccessor forwardIndexAccessor(String searchField) {
        return new ForwardIndexAccessorExternal(this, annotatedField(searchField));
    }

    @Override
    public BLSpanQuery tagQuery(QueryInfo queryInfo, String luceneField, String tagName,
            Map<String, String> attributes) {
        return new SpanQueryTagsExternal(queryInfo, luceneField, tagName, attributes);
    }

    @Override
    public BLSpanQuery relationQuery(QueryInfo queryInfo, String luceneField, String relationType,
            Map<String, String> attributes) {
        throw new UnsupportedOperationException("Relations queries are not supported with the external index format.");
    }

    @Override
    public IndexType getType() {
        return IndexType.EXTERNAL_FILES;
    }

    @Override
    public Query getAllRealDocsQuery() {
        return new MatchAllDocsQuery(); // there are no non-real documents in this index type
    }

    @Override
    public boolean needsPrimaryValuePayloads() {
        return false;
    }

    @Override
    public Document luceneDoc(int docId, boolean includeContentStores) {
        if (includeContentStores)
            throw new UnsupportedOperationException("External index format always skips content stores");
        try {
            return reader().document(docId);
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    @Override
    public void delete(Query q) {
        logger.debug("Delete query: " + q);
        if (!indexMode())
            throw new BlackLabRuntimeException("Cannot delete documents, not in index mode");
        try {
            // We have to delete the document from the CS and FI separately.

            // Open a fresh reader to execute the query
            IndexWriter luceneIndexWriter = ((BLIndexWriterProxyLucene)indexWriter).getWriter();
            try (IndexReader freshReader = DirectoryReader.open(luceneIndexWriter, false,
                    false)) {
                // Execute the query, iterate over the docs and delete from FI and CS.
                IndexSearcher s = new IndexSearcher(freshReader);
                Weight w = s.createWeight(q, ScoreMode.COMPLETE_NO_SCORES, 1.0f);
                logger.debug("Doing delete. Number of leaves: " + freshReader.leaves().size());
                for (LeafReaderContext leafContext: freshReader.leaves()) {
                    Bits liveDocs = leafContext.reader().getLiveDocs();

                    Scorer scorer = w.scorer(leafContext);
                    if (scorer == null) {
                        logger.debug("  No hits in leafcontext");
                        continue; // no matching documents
                    }

                    // Iterate over matching docs
                    DocIdSetIterator it = scorer.iterator();
                    logger.debug("  Iterate over matching docs in leaf");
                    while (true) {
                        int docId = it.nextDoc();
                        if (docId == DocIdSetIterator.NO_MORE_DOCS)
                            break;
                        if (liveDocs != null && !liveDocs.get(docId)) {
                            // already deleted.
                            continue;
                        }
                        docId += leafContext.docBase;
                        Document d = freshReader.document(docId);
                        logger.debug(
                                "    About to delete docId " + docId + ", fromInputFile=" + d.get("fromInputFile")
                                        + " from FI and CS");

                        deleteFromForwardIndices(d);

                        // Delete this document in all content stores
                        contentStores.deleteDocument(d);
                    }
                }
            }

            // Finally, delete the documents from the Lucene index
            logger.debug("  Delete docs from Lucene index");
            indexWriter.deleteDocuments(q);

        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

}
