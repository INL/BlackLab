package nl.inl.blacklab.server.lib;

public class IndexUtil {
    public static boolean isUserIndex(String indexName) {
        return indexName.contains(":");
    }
}
