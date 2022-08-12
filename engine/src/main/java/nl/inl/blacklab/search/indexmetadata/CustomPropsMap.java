package nl.inl.blacklab.search.indexmetadata;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic custom properties class that wrap a map.
 */
public class CustomPropsMap implements CustomProps {

    private final Map<String, Object> customFields = new HashMap<>();

    public CustomPropsMap() { }

    public CustomPropsMap(Map<String, Object> props) {
        customFields.putAll(props);
    }

    @Override
    public Object get(String key) {
        return customFields.get(key);
    }

    public void put(String key, Object value) {
        customFields.put(key, value);
    }

    @Override
    public Map<String, Object> asMap() {
        return Collections.unmodifiableMap(customFields);
    }
}
