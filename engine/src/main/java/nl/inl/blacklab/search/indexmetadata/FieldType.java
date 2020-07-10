package nl.inl.blacklab.search.indexmetadata;

/** Possible types of metadata fields. */
public enum FieldType {
    TOKENIZED,
    NUMERIC,
    UNTOKENIZED;

    public static FieldType fromStringValue(String v) {
        switch (v.toLowerCase()) {
        case "tokenized":
        case "text": // deprecated
            return TOKENIZED;
        case "untokenized":
            return UNTOKENIZED;
        case "numeric":
            return NUMERIC;
        default:
            throw new IllegalArgumentException(
                    "Unknown string value for FieldType: " + v + " (should be tokenized|untokenized|numeric)");
        }
    }

    public String stringValue() {
        return toString().toLowerCase();
    }
}
