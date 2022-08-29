package nl.inl.blacklab.search;

import java.io.File;

import javax.xml.bind.annotation.XmlTransient;

import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.MatchAllDocsQuery;
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
    public IndexMetadataIntegrated metadata() {
        return (IndexMetadataIntegrated)super.metadata();
    }

    @Override
    @XmlTransient
    public Query getAllRealDocsQuery() {
        // NOTE: we cannot use Lucene's MatchAllDocsQuery because we need to skip the index metadata document.

        // Get all documents, but make sure to skip the index metadata document.
        // We do this by finding all docs that don't have the metadata marker.
        // (previously, we used new DocValuesFieldExistsQuery(mainAnnotatedField().tokenLengthField())),
        //  so all docs that don't have a value for the main annotated field, but that's not really correct)
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(new MatchAllDocsQuery(), BooleanClause.Occur.SHOULD);
        builder.add(metadata().metadataDocQuery(), BooleanClause.Occur.MUST_NOT);
        return builder.build();
    }

}
