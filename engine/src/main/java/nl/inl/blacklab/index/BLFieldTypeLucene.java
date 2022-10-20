package nl.inl.blacklab.index;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexableFieldType;

import nl.inl.blacklab.search.BlackLabIndexIntegrated;

public class BLFieldTypeLucene implements BLFieldType {

    /** How to index metadata fields (tokenized) */
    public static BLFieldType METADATA_TOKENIZED;

    /** How to index metadata fields (untokenized) */
    public static BLFieldType METADATA_UNTOKENIZED;

    private static Map<String, BLFieldType> fieldTypeCache = new HashMap<>();

    static {
        createMetadataFieldTypes();
    }

    private static void createMetadataFieldTypes() {
        FieldType tokenized = new FieldType();
        tokenized.setStored(true);
        //tokenized.setIndexed(true);
        tokenized.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        tokenized.setTokenized(true);
        tokenized.setOmitNorms(true); // <-- depending on setting?
        tokenized.setStoreTermVectors(true);
        tokenized.setStoreTermVectorPositions(true);
        tokenized.setStoreTermVectorOffsets(true);
        tokenized.freeze();
        METADATA_TOKENIZED = new BLFieldTypeLucene(tokenized);

        FieldType untokenized = new FieldType(tokenized);
        untokenized.setIndexOptions(IndexOptions.DOCS_AND_FREQS);
        //untokenized.setTokenized(false);  // <-- this should be done with KeywordAnalyzer, otherwise untokenized fields aren't lowercased
        untokenized.setStoreTermVectors(false);
        untokenized.setStoreTermVectorPositions(false);
        untokenized.setStoreTermVectorOffsets(false);
        untokenized.freeze();
        METADATA_UNTOKENIZED = new BLFieldTypeLucene(untokenized);
    }

    public static BLFieldType metadata(boolean tokenized) {
        return tokenized ? BLFieldTypeLucene.METADATA_TOKENIZED : BLFieldTypeLucene.METADATA_UNTOKENIZED;
    }

    /**
     * Get the appropriate FieldType given the options for an annotation sensitivity.
     */
    public static synchronized BLFieldType annotationSensitivity(boolean offsets, boolean forwardIndex, boolean contentStore) {
        String key = (offsets ? "O" : "-") + (forwardIndex ? "F" : "-") + (contentStore ? "C" : "-");
        return fieldTypeCache.computeIfAbsent(key, (__) -> {
            IndexOptions indexOptions = offsets ?
                    IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS :
                    IndexOptions.DOCS_AND_FREQS_AND_POSITIONS;

            FieldType type = new FieldType();
            type.setIndexOptions(indexOptions);
            type.setTokenized(true);
            type.setOmitNorms(true);
            type.setStored(contentStore);
            type.setStoreTermVectors(true);
            type.setStoreTermVectorPositions(true);
            type.setStoreTermVectorOffsets(offsets);
            if (contentStore) {
                // store field in content store (for random access)
                // (we set this regardless of our index format, but that's okay, it doesn't hurt anything if not used)
                BlackLabIndexIntegrated.setContentStoreField(type);
            }
            if (forwardIndex) {
                // store field in content store (for random access)
                BlackLabIndexIntegrated.setForwardIndexField(type);
            }
            type.freeze();
            return new BLFieldTypeLucene(type);
        });
    }



    private final IndexableFieldType type;

    public BLFieldTypeLucene(IndexableFieldType type) {
        this.type = type;
    }

    public IndexableFieldType getLuceneFieldType() {
        return type;
    }

}
