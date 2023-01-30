package org.ivdnt.blacklab.proxy.helper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBElement;

import org.ivdnt.blacklab.proxy.representation.Annotation;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class SerializationUtil {

    /** Return a XML-safe attribute.
     *
     * Might want to add camel case support.
     *
     * @param attributeLabel proposed label
     * @return sanitized version
     */
    public static String getCleanLabel(String attributeLabel) {
        attributeLabel = attributeLabel.replaceAll("[()]", "").replaceAll("[^\\w\\s]", "_").replaceAll(" ", "_");
        return attributeLabel;
    }

    /** Use this to serialize a String map using MapAdapter to JSON.
     */
    public static class StringMapSerializer extends JsonSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {
            if (value instanceof MapAdapter.MapWrapper) {
                // This happens because of using MapAdapter with Jersey
                jgen.writeStartObject();
                for (Object element: ((MapAdapter.MapWrapper) value).elements) {
                    JAXBElement el = (JAXBElement) element;
                    jgen.writeStringField(el.getName().getLocalPart(), el.getValue().toString());
                }
                jgen.writeEndObject();
            } else if (value instanceof Map) {
                // This happens if we use Jackson directly without Jersey
                jgen.writeStartObject();
                for (Map.Entry<String, String> entry: ((Map<String, String>) value).entrySet()) {
                    jgen.writeStringField(entry.getKey(), entry.getValue());
                }
                jgen.writeEndObject();
            }
        }
    }

    /** Use this to deserialize a String map using MapAdapter from JSON.
     *
     * Necessary because we convert a JSON object structure to a list (because that's what the XML mapping uses).
     */
    public static class StringMapDeserializer extends JsonDeserializer<Map<String, String>> {

        @Override
        public Map<String, String> deserialize(JsonParser parser, DeserializationContext deserializationContext)
                throws IOException {
            return readStringMap(parser);
        }
    }

    private SerializationUtil() {}

    public static List<String> readStringList(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token != JsonToken.START_ARRAY)
            throw new RuntimeException("Expected START_ARRAY, found " + token);

        List<String> list = new ArrayList<>();
        while (true) {
            token = parser.nextToken();
            if (token == JsonToken.END_ARRAY)
                break;
            if (token != JsonToken.VALUE_STRING)
                throw new RuntimeException("Expected END_ARRAY or VALUE_STRING, found " + token);
            list.add(parser.getValueAsString());
        }
        return list;
    }

    public static List<Annotation> readAnnotations(JsonParser parser) throws IOException {
        List<Annotation> result = new ArrayList<>();
        while (true) {
            JsonToken token = parser.nextToken();
            if (token == JsonToken.END_OBJECT)
                break;

            if (token != JsonToken.FIELD_NAME)
                throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
            Annotation annotation = new Annotation();
            annotation.name = parser.getCurrentName();

            token = parser.nextToken();
            if (token != JsonToken.START_OBJECT)
                throw new RuntimeException("Expected END_OBJECT or START_OBJECT, found " + token);
            while (true) {
                token = parser.nextToken();
                if (token == JsonToken.END_OBJECT)
                    break;

                if (token != JsonToken.FIELD_NAME)
                    throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
                String fieldName = parser.getCurrentName();
                parser.nextToken();
                switch (fieldName) {
                case "displayName": annotation.displayName = parser.getValueAsString(); break;
                case "description": annotation.description = parser.getValueAsString(); break;
                case "uiType": annotation.uiType = parser.getValueAsString(); break;
                case "hasForwardIndex": annotation.hasForwardIndex = parser.getValueAsBoolean(); break;
                case "sensitivity": annotation.sensitivity = parser.getValueAsString(); break;
                case "offsetsAlternative": annotation.offsetsAlternative = parser.getValueAsString(); break;
                case "isInternal": annotation.isInternal = parser.getValueAsBoolean(); break;
                case "subannotations": annotation.subannotations = readStringList(parser); break;
                case "parentAnnotation": annotation.parentAnnotation = parser.getValueAsString(); break;
                case "values": annotation.values = readStringList(parser); break;
                case "valueListComplete": annotation.valueListComplete = parser.getValueAsBoolean(); break;
                default: throw new RuntimeException("Unexpected field " + fieldName + " in Annotation");
                }
            }

            result.add(annotation);
        }
        return result;
    }

    public static Map<String, String> readStringMap(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token != JsonToken.START_OBJECT)
            throw new RuntimeException("Expected START_OBJECT, found " + token);

        Map<String, String> result = new LinkedHashMap<>();
        while (true) {
            token = parser.nextToken();
            if (token == JsonToken.END_OBJECT)
                break;

            if (token != JsonToken.FIELD_NAME)
                throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
            String key = parser.getCurrentName();

            token = parser.nextToken();
            if (token != JsonToken.VALUE_STRING)
                throw new RuntimeException("Expected VALUE_STRING, found " + token);
            String value = parser.getValueAsString();

            result.put(key, value);
        }
        return result;
    }

    public static Map<String, Integer> readIntegerMap(JsonParser parser) throws IOException {
        JsonToken token = parser.currentToken();
        if (token != JsonToken.START_OBJECT)
            throw new RuntimeException("Expected START_OBJECT, found " + token);

        Map<String, Integer> result = new LinkedHashMap<>();
        while (true) {
            token = parser.nextToken();
            if (token == JsonToken.END_OBJECT)
                break;

            if (token != JsonToken.FIELD_NAME)
                throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
            String key = parser.getCurrentName();

            token = parser.nextToken();
            int value;
            switch (token) {
            case VALUE_NUMBER_INT:
                value = parser.getValueAsInt();
                break;
            case VALUE_STRING: // fieldValues accidentally sends numbers as string, compensate...
                String strValue = parser.getValueAsString();
                value = Integer.parseInt(strValue);
                break;
            default:
                throw new RuntimeException("Expected VALUE_NUMBER_INT or VALUE_STRING, found " + token);
            }

            result.put(key, value);
        }
        return result;
    }

}
