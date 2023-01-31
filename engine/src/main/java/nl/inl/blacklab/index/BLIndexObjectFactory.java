package nl.inl.blacklab.index;

import org.apache.lucene.index.IndexWriter;

import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.FieldType;

/**
 * Factory object for objects related to indexing.
 *
 * Specifically, returns instances of BLInputDocument and BLFieldType.
 */
public interface BLIndexObjectFactory {
    BLInputDocument createInputDocument();

    BLFieldType fieldTypeMetadata(boolean tokenized);

    BLFieldType fieldTypeContentStore();

    BLFieldType fieldTypeAnnotationSensitivity(boolean offsets, boolean forwardIndex);

    BLFieldType fieldTypeIndexMetadataMarker();

    BLIndexWriterProxy indexWriterProxy(IndexWriter luceneIndexWriter, BlackLabIndexWriter indexWriter);

    default BLFieldType blFieldTypeFromMetadataFieldType(FieldType type) {
        switch (type) {
        case NUMERIC:
           throw new IllegalArgumentException("Numeric types should be indexed using IntField, etc.");
        case TOKENIZED:
           return fieldTypeMetadata(true);
        case UNTOKENIZED:
           return fieldTypeMetadata(false);
        default:
           throw new IllegalArgumentException("Unknown field type: " + type);
        }
    }
}
