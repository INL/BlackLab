package nl.inl.blacklab.index;

import org.apache.lucene.index.IndexWriter;

import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.blacklab.search.indexmetadata.FieldType;

/**
 * Factory object for objects related to indexing.
 * This interface is mainly responsible for supplying those objects that need to be aware of the type of index this is (solr or lucene), but do not need details specific to the shape and data in the index.
 * Currently the FieldType and IndexWriter (which is responsible for saving the documents to lucene/solr).
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
