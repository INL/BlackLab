package nl.inl.blacklab.index.annotated;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexOptions;

import nl.inl.blacklab.search.BlackLabIndexIntegrated;

/**
 * Provides Lucene FieldTypes for annotated fields.
 */
public class BLAnnotFieldTypes {
    private static Map<String, FieldType> fieldTypeCache = new HashMap<>();

    /**
     * Get the appropriate FieldType given the options for an annotation sensitivity.
     */
    public static synchronized FieldType get(boolean offsets, boolean forwardIndex, boolean contentStore) {
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
                BlackLabIndexIntegrated.setContentStoreFIeld(type);
            }
            if (forwardIndex) {
                // store field in content store (for random access)
                BlackLabIndexIntegrated.setForwardIndexField(type);
            }
            type.freeze();
            return type;
        });
    }
}
