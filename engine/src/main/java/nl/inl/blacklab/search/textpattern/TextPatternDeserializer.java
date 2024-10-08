package nl.inl.blacklab.search.textpattern;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import nl.inl.blacklab.search.matchfilter.TextPatternStruct;

public class TextPatternDeserializer extends JsonDeserializer<TextPatternStruct> {

    @Override
    public TextPatternStruct deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        JsonToken token = parser.currentToken();
        if (token != JsonToken.START_OBJECT)
            throw new RuntimeException("Expected START_OBJECT, found " + token);
        Object tp = readObject(parser);
        assert tp instanceof TextPatternStruct; // not a MatchFilter (those can only be part of a TextPattern)
        return (TextPatternStruct) tp;
    }

    private Object readObject(JsonParser parser) throws IOException {
        JsonToken token;
        String nodeType = null;
        Map<String, Object> args = new LinkedHashMap<>();
        while (true) {
            token = parser.nextToken();
            if (token == JsonToken.END_OBJECT)
                break;
            if (token != JsonToken.FIELD_NAME)
                throw new RuntimeException("Expected END_OBJECT or FIELD_NAME, found " + token);
            String fieldName = parser.getCurrentName();
            parser.nextToken();
            Object value = readValue(fieldName, parser);
            if ("type".equals(fieldName)) {
                assert value instanceof String : "node type must be a string";
                nodeType = (String) value;
            } else {
                args.put(fieldName, value);
            }
        }
        assert nodeType != null : "each TextPattern node must have a type";
        return TextPatternSerializerJson.deserialize(nodeType, args);
    }

    private Object readValue(String fieldName, JsonParser parser)
            throws IOException {
        JsonToken token = parser.currentToken();
        Object value = null;
        switch (token) {
        case VALUE_FALSE:
            value = false;
            break;
        case VALUE_TRUE:
            value = true;
            break;
        case VALUE_NULL:
            value = null; // illegal, see below
            break;
        case VALUE_STRING:
            value = parser.getValueAsString();
            break;
        case VALUE_NUMBER_INT:
            value = parser.getValueAsInt();
            break;
        case VALUE_NUMBER_FLOAT:
            value = parser.getValueAsDouble();
            break;
        case START_ARRAY:
            value = readArray(parser);
            break;
        case START_OBJECT:
            if (fieldName.equals(TextPatternSerializerJson.KEY_ATTRIBUTES)) {
                // Attributes to a "tags" TextPattern.
                value = readStringMap(parser);
            } else {
                // A TextPattern or MatchFilter (determined by "type")
                value = readObject(parser);
            }
        }
        assert value != null: "no field value found";
        return value;
    }

    private List<Object> readArray(JsonParser parser) throws IOException {
        List<Object> list = new ArrayList<>();
        while (true) {
            JsonToken token = parser.nextToken();
            if (token == JsonToken.END_ARRAY)
                break;
            list.add(readValue("", parser));
        }
        return list;
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

}
