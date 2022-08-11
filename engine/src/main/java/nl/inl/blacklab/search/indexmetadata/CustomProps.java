package nl.inl.blacklab.search.indexmetadata;

import java.util.Map;

/**
 * Interface for getting custom metadata properties (not used by BlackLab itself)
 */
public interface CustomProps {

    /** Delegate object for corpus-level custom metadata (legacy) */
    static CustomProps corpusDelegate(IndexMetadata metadata) {
        return new CustomPropsCorpusDelegate(metadata);
    }

    String get(String key, String defaultValue);

    default String get(String key) {
        return get(key, null);
    }

    Map<String, String> asMap();
}
