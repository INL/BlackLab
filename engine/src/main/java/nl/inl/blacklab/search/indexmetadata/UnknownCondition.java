package nl.inl.blacklab.search.indexmetadata;

/** Conditions for using the unknown value */
public enum UnknownCondition {
    NEVER, // never use unknown value
    MISSING, // use unknown value when field is missing (not when empty)
    EMPTY, // use unknown value when field is empty (not when missing)
    MISSING_OR_EMPTY; // use unknown value when field is empty or missing

    public static UnknownCondition fromStringValue(String string) {
        return valueOf(string.toUpperCase());
    }

    public String stringValue() {
        return toString().toLowerCase();
    }
}