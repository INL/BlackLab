package nl.inl.blacklab.search.indexmetadata;

import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.util.Json;

/**
 * Interface for getting custom metadata properties (not used by BlackLab itself)
 */
public interface CustomProps {

    static CustomPropsMap fromJson(ObjectNode nodeCustom) {
        try {
            Map<String, Object> props = Json.getJsonObjectMapper().treeToValue(nodeCustom, Map.class);
            return new CustomPropsMap(props);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    CustomProps NONE = new CustomProps() {
        @Override
        public Object get(String key) {
            return null;
        }

        @Override
        public Map<String, Object> asMap() {
            return Collections.emptyMap();
        }
    };

    default <T> T get(String key, T defaultValue) {
        T x = (T)get(key);
        return x == null ? defaultValue : x;
    }

    Object get(String key);

    Map<String, Object> asMap();

    default boolean containsKey(String key) {
        return get(key) != null;
    }
}
