package nl.inl.blacklab.search.indexmetadata;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Generic custom properties class that wrap a map.
 */
@XmlAccessorType(XmlAccessType.FIELD)
@JsonSerialize(using = CustomPropsMap.Serializer.class)
@JsonDeserialize(using = CustomPropsMap.Deserializer.class)
public class CustomPropsMap implements CustomProps {

    /** Use this to serialize this class to JSON */
    public static class Serializer extends JsonSerializer<CustomPropsMap> {
        @Override
        public void serialize(CustomPropsMap el, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            if (el == null)
                return;
            provider.defaultSerializeValue(el.customFields, jgen);
        }
    }

    public static class Deserializer extends JsonDeserializer<CustomPropsMap> {
        @Override
        public CustomPropsMap deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {
            JsonToken token = parser.getCurrentToken();
            if (token != JsonToken.START_OBJECT)
                throw new RuntimeException("Expected START_OBJECT, found " + token);
            return new CustomPropsMap(parser.readValueAs(Map.class));
        }
    }

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
