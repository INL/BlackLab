package nl.inl.blacklab.index;

import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;

class BLDocumentFactoryLucene implements BLDocumentFactory {
    public static BLDocumentFactoryLucene INSTANCE = new BLDocumentFactoryLucene();

    private static BLFieldTypeLucene indexMetadataMarkerFieldType;

    static {
        FieldType marker = new org.apache.lucene.document.FieldType();
        marker.setIndexOptions(IndexOptions.DOCS);
        marker.setTokenized(false);
        marker.setOmitNorms(true);
        marker.setStored(false);
        marker.setStoreTermVectors(false);
        marker.setStoreTermVectorPositions(false);
        marker.setStoreTermVectorOffsets(false);
        marker.freeze();
        indexMetadataMarkerFieldType = new BLFieldTypeLucene(marker);
    }

    private BLDocumentFactoryLucene() {}

    public BLInputDocument create() {
        return new BLInputDocumentLucene();
    }

    @Override
    public BLFieldType fieldTypeMetadata(boolean tokenized) {
        return BLFieldTypeLucene.metadata(tokenized);
    }

    @Override
    public BLFieldType fieldTypeAnnotationSensitivity(boolean offsets, boolean forwardIndex, boolean contentStore) {
        return BLFieldTypeLucene.annotationSensitivity(offsets, forwardIndex, contentStore);
    }

    public BLFieldType fieldTypeIndexMetadataMarker() {
        return indexMetadataMarkerFieldType;
    }

    @Override
    public BLIndexWriterProxy indexWriterProxy(IndexWriter luceneIndexWriter) {
        return new BLIndexWriterProxyLucene(luceneIndexWriter);
    }
}
