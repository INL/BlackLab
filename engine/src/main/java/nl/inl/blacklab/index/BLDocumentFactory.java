package nl.inl.blacklab.index;

import org.apache.lucene.index.IndexWriter;

public interface BLDocumentFactory {
    public static BLDocumentFactory get(boolean runningFromSolr) {
         return runningFromSolr ? null/*BLDocumentFactorySolr.INSTANCE*/ : BLDocumentFactoryLucene.INSTANCE;
    }

    BLInputDocument create();

    BLFieldType fieldTypeMetadata(boolean tokenized);

    BLFieldType fieldTypeAnnotationSensitivity(boolean offsets, boolean forwardIndex, boolean contentStore);

    BLFieldType fieldTypeIndexMetadataMarker();

    BLIndexWriterProxy indexWriterProxy(IndexWriter luceneIndexWriter);
}
