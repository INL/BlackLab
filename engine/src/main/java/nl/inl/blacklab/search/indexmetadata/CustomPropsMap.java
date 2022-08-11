package nl.inl.blacklab.search.indexmetadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic custom properties class that wrap a map.
 */
class CustomPropsMap implements CustomProps {

    private final Map<String, String> customFields = new HashMap<>();

    @Override
    public String get(String key, String defaultValue) {
        return customFields.getOrDefault(key, defaultValue);
    }

    public void put(String key, String value) {
        customFields.put(key, value);
    }

    @Override
    public Map<String, String> asMap() {
        return Collections.unmodifiableMap(customFields);
    }
}
