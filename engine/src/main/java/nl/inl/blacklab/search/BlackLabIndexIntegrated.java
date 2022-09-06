package nl.inl.blacklab.search;

import java.io.File;

import javax.xml.bind.annotation.XmlTransient;

import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.FieldInfo;
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

    /** Lucene field attribute. Does the field have a forward index?
        If yes, payloads will indicate primary/secondary values. */
    private static final String BLFA_FORWARD_INDEX = "BL_hasForwardIndex";

    /** Lucene field attribute. Does the field have a content store */
    private static final String BLFA_CONTENT_STORE = "BL_hasContentStore";

    /**
     * Does the specified Lucene field have a forward index stored with it?
     *
     * If yes, we can deduce from the payload if a value is the primary value (e.g. original word,
     * to use for concordances, sort, group, etc.) or a secondary value (e.g. stemmed, synonym).
     * This is used because we only store the primary value in the forward index.
     *
     * We need to know this whenever we work with payloads too, so we can skip this indicator.
     * See {@link nl.inl.blacklab.analysis.PayloadUtils}.
     *
     * @param fieldInfo Lucene field to check
     * @return true if it's a forward index field
     */
    public static boolean isForwardIndexField(FieldInfo fieldInfo) {
        String v = fieldInfo.getAttribute(BLFA_FORWARD_INDEX);
        return v != null && v.equals("true");
    }

    /**
     * Set this field type to be a forward index field
     * @param type field type
     */
    public static void setForwardIndexField(FieldType type) {
        type.putAttribute(BlackLabIndexIntegrated.BLFA_FORWARD_INDEX, "true");
    }

    /**
     * Is the specified field a content store field?
     *
     * @param fieldInfo field to check
     * @return true if it's a content store field
     */
    public static boolean isContentStoreField(FieldInfo fieldInfo) {
        String v = fieldInfo.getAttribute(BLFA_CONTENT_STORE);
        return v != null && v.equals("true");
    }

    /**
     * Set this field type to be a content store field
     * @param type field type
     */
    public static void setContentStoreFIeld(FieldType type) {
        type.putAttribute(BlackLabIndexIntegrated.BLFA_CONTENT_STORE, "true");
    }

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
        config.setCodec(new BlackLab40Codec()); // our own custom codec (extended from Lucene)

        // disabling this can speed up indexing a bit but also uses a lot of file descriptors;
        // it can be useful to see individual files during development. maybe make this configurable?
        config.setUseCompoundFile(false);
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
    public boolean needsPrimaryValuePayloads() {
        // we need these because we store the forward index when the segment is about to be written,
        // at which point this information would otherwise be lost.
        return true;
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
