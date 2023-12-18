package nl.inl.blacklab.indexers.config;

public enum AnnotationType {
    TOKEN,
    SPAN,
    RELATION;

    public static AnnotationType fromStringValue(String t) {
        switch (t.toLowerCase()) {
        case "token":
            return TOKEN;
        case "span":
            return SPAN;
        case "relation":
            return RELATION;
        }
        throw new IllegalArgumentException("Unknown standoff annotation type: " + t);
    }

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
