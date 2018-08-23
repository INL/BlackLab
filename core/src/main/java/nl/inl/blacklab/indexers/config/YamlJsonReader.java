package nl.inl.blacklab.indexers.config;

import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ValueNode;

import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;

/**
 * Base class for a class that interprets a JSON structure.
 *
 * Contains utility methods that make it easy to check types and throw
 * descriptive errors.
 */
public class YamlJsonReader {

    public static ObjectNode obj(JsonNode node, String name) {
        if (!(node instanceof ObjectNode))
            throw new InvalidInputFormatConfig(name + " must be an object");
        return (ObjectNode) node;
    }

    public static ObjectNode obj(Entry<String, JsonNode> e) {
        return obj(e.getValue(), e.getKey());
    }

    public static ArrayNode array(JsonNode node, String name) {
        if (!(node instanceof ArrayNode))
            throw new InvalidInputFormatConfig(name + " must be an array");
        return (ArrayNode) node;
    }

    public static ArrayNode array(Entry<String, JsonNode> e) {
        return array(e.getValue(), e.getKey());
    }

    public static String str(JsonNode node, String name) {
        if (!(node instanceof ValueNode))
            throw new InvalidInputFormatConfig(name + " must be a string value");
        return node.asText();
    }

    public static String str(Entry<String, JsonNode> e) {
        return str(e.getValue(), e.getKey());
    }

    public static boolean bool(JsonNode node, String name) {
        if (!(node instanceof ValueNode))
            throw new InvalidInputFormatConfig(name + " must be a boolean value");
        return node.asBoolean();
    }

    public static boolean bool(Entry<String, JsonNode> e) {
        return bool(e.getValue(), e.getKey());
    }

    public static int integer(JsonNode node, String name) {
        if (!(node instanceof ValueNode))
            throw new InvalidInputFormatConfig(name + " must be an integer value");
        return node.asInt();
    }

    public static int integer(Entry<String, JsonNode> e) {
        return integer(e.getValue(), e.getKey());
    }

    public static long longint(JsonNode node, String name) {
        if (!(node instanceof ValueNode))
            throw new InvalidInputFormatConfig(name + " must be a (long) integer value");
        return node.asLong();
    }

    public static long longint(Entry<String, JsonNode> e) {
        return longint(e.getValue(), e.getKey());
    }

    public static double numeric(JsonNode node, String name) {
        if (!(node instanceof ValueNode))
            throw new InvalidInputFormatConfig(name + " must be a numeric value");
        return node.asDouble();
    }

    public static double numeric(Entry<String, JsonNode> e) {
        return numeric(e.getValue(), e.getKey());
    }

    public static char character(JsonNode node, String name) {
        if (!(node instanceof ValueNode))
            throw new InvalidInputFormatConfig(name + " must be a single character");
        String txt = node.asText();
        if (txt.length() != 1)
            throw new InvalidInputFormatConfig(name + " must be a single character");
        return txt.charAt(0);
    }

    public static char character(Entry<String, JsonNode> e) {
        return character(e.getValue(), e.getKey());
    }

}
