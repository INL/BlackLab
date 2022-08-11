package nl.inl.blacklab.search.indexmetadata;

import java.util.Map;

/**
 * Custom properties at corpus level that delegates to the old methods.
 */
@SuppressWarnings("deprecation")
class CustomPropsCorpusDelegate implements CustomProps {
    private final IndexMetadata indexMetadata;

    public CustomPropsCorpusDelegate(IndexMetadata indexMetadata) {
        this.indexMetadata = indexMetadata;
    }

    @Override
    public String get(String key, String defaultValue) {
        switch (key) {
        case "displayName":
            return indexMetadata.displayName();
        case "description":
            return indexMetadata.description();
        case "textDirection":
            return indexMetadata.textDirection().getCode();
        default:
            return defaultValue;
        }
    }

    @Override
    public Map<String, String> asMap() {
        return Map.of(
                "displayName", indexMetadata.displayName(),
                "description", indexMetadata.description(),
                "textDirection", indexMetadata.textDirection().getCode()
        );
    }
}
