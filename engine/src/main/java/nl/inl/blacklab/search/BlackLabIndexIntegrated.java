package nl.inl.blacklab.search;

import java.io.File;

import javax.xml.bind.annotation.XmlTransient;

import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.DocValuesFieldExistsQuery;
import org.apache.lucene.search.Query;

import nl.inl.blacklab.codec.BlackLab40Codec;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndexIntegrated;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorIntegrated;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataIntegrated;
import nl.inl.blacklab.search.indexmetadata.IndexMetadataWriter;

/**
 * A BlackLab index with all files included in the Lucene index.
 */
public class BlackLabIndexIntegrated extends BlackLabIndexAbstract {

    BlackLabIndexIntegrated(BlackLabEngine blackLab, File indexDir, boolean indexMode, boolean createNewIndex,
            ConfigInputFormat config) throws ErrorOpeningIndex {
        super(blackLab, indexDir, indexMode, createNewIndex, config);
    }

    BlackLabIndexIntegrated(BlackLabEngine blackLab, File indexDir, boolean indexMode, boolean createNewIndex,
            File indexTemplateFile) throws ErrorOpeningIndex {
        super(blackLab, indexDir, indexMode, createNewIndex, indexTemplateFile);
    }

    protected IndexMetadataWriter getIndexMetadata(boolean createNewIndex, ConfigInputFormat config) {
        if (!createNewIndex)
            return IndexMetadataIntegrated.deserializeFromJsonJaxb(this);
        return IndexMetadataIntegrated.create(this, config);
    }

    protected IndexMetadataWriter getIndexMetadata(boolean createNewIndex, File indexTemplateFile) {
        if (!createNewIndex)
            return IndexMetadataIntegrated.deserializeFromJsonJaxb(this);
        if (indexTemplateFile != null)
            throw new UnsupportedOperationException(
                    "Template file not supported for integrated index format! Please see the IndexTool documentation for how use the classic index format.");
        return IndexMetadataIntegrated.create(this, null);
    }

    public ForwardIndex createForwardIndex(AnnotatedField field) {
        return new ForwardIndexIntegrated(this, field);
    }

    @Override
    protected void customizeIndexWriterConfig(IndexWriterConfig config) {
        config.setCodec(new BlackLab40Codec(this)); // our own custom codec (extended from Lucene)
        config.setUseCompoundFile(false); // @@@ TEST
    }

    @Override
    public ForwardIndexAccessor forwardIndexAccessor(String searchField) {
        return new ForwardIndexAccessorIntegrated(this, annotatedField(searchField));
    }

    @Override
    @XmlTransient
    public Query getAllRealDocsQuery() {
        // NOTE: we cannot use Lucene's MatchAllDocsQuery because we need to skip the index metadata document.

        // Get all documents, but make sure to skip the index metadata document by only getting documents
        // that have a value for the main annotated field.
        // TODO: arguably this is wrong, because it is valid to add documents that do not have a value
        //    for the main annotated field. Perhaps there are others, or perhaps you want a metadata-only
        //    document. But no-one seems to use this, and the rest of the code may not actually handle it
        //    either. Ideally, this would be possible though, and we would need a query that only skips the
        //    index metadata document. If we remember its docId, that's easy.
        return new DocValuesFieldExistsQuery(mainAnnotatedField().tokenLengthField());
    }

}
