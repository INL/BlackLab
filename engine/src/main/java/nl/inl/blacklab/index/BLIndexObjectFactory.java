package nl.inl.blacklab.index;

import org.apache.lucene.index.IndexWriter;

/**
 * Factory object for objects related to indexing.
 *
 * Specifically, returns instances of BLInputDocument and BLFieldType.
 */
public interface BLIndexObjectFactory {
    public static BLIndexObjectFactory get(boolean runningFromSolr) {
         return runningFromSolr ? null/*BLIndexObjectFactorySolr.INSTANCE*/ : BLIndexObjectFactoryLucene.INSTANCE;
    }

    BLInputDocument createInputDocument();

    BLFieldType fieldTypeMetadata(boolean tokenized);

    BLFieldType fieldTypeAnnotationSensitivity(boolean offsets, boolean forwardIndex, boolean contentStore);

    BLFieldType fieldTypeIndexMetadataMarker();

    BLIndexWriterProxy indexWriterProxy(IndexWriter luceneIndexWriter);
}
