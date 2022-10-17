package nl.inl.blacklab.server.exceptions;

public class IllegalIndexName extends BadRequest {

    public static final String ILLEGAL_NAME_ERROR = "is not a valid index name (only letters, digits, dots, underscores and dashes allowed, and must start with a letter)";

    public IllegalIndexName(String indexName) {
        super("ILLEGAL_INDEX_NAME", "\"" + shortName(indexName) + "\" " + ILLEGAL_NAME_ERROR);
    }

    private static String shortName(String indexName) {
        int colonAt = indexName.indexOf(":");
        if (colonAt >= 0)
            return indexName.substring(colonAt + 1);
        return indexName;
    }

}
