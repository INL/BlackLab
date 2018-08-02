package nl.inl.blacklab.server.exceptions;

import nl.inl.blacklab.server.requesthandlers.Response;

public class IllegalIndexName extends BadRequest {

    public IllegalIndexName(String indexName) {
        super("ILLEGAL_INDEX_NAME", "\"" + shortName(indexName) + "\" " + Response.ILLEGAL_NAME_ERROR);
    }

    private static String shortName(String indexName) {
        int colonAt = indexName.indexOf(":");
        if (colonAt >= 0)
            return indexName.substring(colonAt + 1);
        return indexName;
    }

}
