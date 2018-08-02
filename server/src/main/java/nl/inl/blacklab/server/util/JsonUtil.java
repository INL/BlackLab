package nl.inl.blacklab.server.util;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class JsonUtil {

    public static String getProperty(JsonNode obj, String key, String defVal) {
        if (!obj.has(key))
            return defVal;
        return obj.get(key).textValue();
    }

    public static File getFileProp(JsonNode obj, String key, File defVal) {
        if (!obj.has(key))
            return defVal;
        return new File(obj.get(key).textValue());
    }

    public static boolean getBooleanProp(JsonNode obj, String key, boolean defVal) {
        if (!obj.has(key))
            return defVal;
        return obj.get(key).booleanValue();
    }

    public static int getIntProp(JsonNode obj, String key, int defVal) {
        if (!obj.has(key))
            return defVal;
        return obj.get(key).intValue();
    }

    public static long getLongProp(JsonNode obj, String key, long defVal) {
        if (!obj.has(key))
            return defVal;
        return obj.get(key).longValue();
    }

    public static double getDoubleProp(JsonNode obj, String key, double defVal) {
        if (!obj.has(key))
            return defVal;
        return obj.get(key).doubleValue();
    }

    /**
     * Performs a deep conversion from JsonNode to Java Map.
     *
     * @param jsonObject the JSON array to convert
     * @return the Java equivalent
     */
    public static Map<String, Object> mapFromJsonObject(JsonNode jsonObject) {
        Map<String, Object> result = new HashMap<>();
        Iterator<Entry<String, JsonNode>> it = jsonObject.fields();
        while (it.hasNext()) {
            Entry<String, JsonNode> entry = it.next();
            result.put(entry.getKey(), fromJsonStruct(entry.getValue()));
        }
        return result;
    }

    /**
     * Performs a deep conversion from JsonNode to Java List.
     *
     * @param jsonArray the JSON array to convert
     * @return the Java equivalent
     */
    private static List<Object> listFromJsonArray(JsonNode jsonArray) {
        List<Object> result = new ArrayList<>();
        for (int i = 0; i < jsonArray.size(); i++) {
            result.add(fromJsonStruct(jsonArray.get(i)));
        }
        return result;
    }

    /**
     * Performs a deep conversion from JSON object/array to Java Map/List. Passes
     * String unchanged.
     *
     * @param jsonStruct the JSON structure to convert
     * @return the Java equivalent
     */
    public static Object fromJsonStruct(JsonNode jsonStruct) {
        if (jsonStruct instanceof ObjectNode)
            return mapFromJsonObject(jsonStruct);
        if (jsonStruct instanceof ArrayNode)
            return listFromJsonArray(jsonStruct);
        if (jsonStruct instanceof TextNode)
            return jsonStruct.textValue();
        throw new IllegalArgumentException(
                "Cannot convert " + jsonStruct.getClass().getSimpleName() + " from JSON- to Java object");
    }

}
