package nl.inl.blacklab.server.util;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

public class JsonUtil {

	public static String getProperty(JSONObject obj, String key, String defVal) {
		if (!obj.has(key))
			return defVal;
		return obj.getString(key);
	}

	public static File getFileProp(JSONObject obj, String key, File defVal) {
		if (!obj.has(key))
			return defVal;
		return new File(obj.getString(key));
	}

	public static boolean getBooleanProp(JSONObject obj, String key, boolean defVal) {
		if (!obj.has(key))
			return defVal;
		return obj.getBoolean(key);
	}

	public static int getIntProp(JSONObject obj, String key, int defVal) {
		if (!obj.has(key))
			return defVal;
		return obj.getInt(key);
	}

	public static long getLongProp(JSONObject obj, String key, long defVal) {
		if (!obj.has(key))
			return defVal;
		return obj.getLong(key);
	}

	public static double getDoubleProp(JSONObject obj, String key, double defVal) {
		if (!obj.has(key))
			return defVal;
		return obj.getDouble(key);
	}

	/**
	 * Performs a deep conversion from JSONObject to
	 * Java Map.
	 *
	 * @param jsonObject the JSON array to convert
	 * @return the Java equivalent
	 */
	public static Map<String, Object> mapFromJsonObject(JSONObject jsonObject) {
		Map<String, Object> result = new HashMap<>();
		for (Object oKey: jsonObject.keySet()) {
			String key = oKey.toString();
			result.put(key, fromJsonStruct(jsonObject.get(key)));
		}
		return result;
	}

	/**
	 * Performs a deep conversion from JSONArray to
	 * Java List.
	 *
	 * @param jsonArray the JSON array to convert
	 * @return the Java equivalent
	 */
	private static List<Object> listFromJsonArray(JSONArray jsonArray) {
		List<Object> result = new ArrayList<>();
		for (int i = 0; i < jsonArray.length(); i++) {
			result.add(fromJsonStruct(jsonArray.get(i)));
		}
		return result;
	}

	/**
	 * Performs a deep conversion from JSON object/array to
	 * Java Map/List. Passes String unchanged.
	 *
	 * @param jsonStruct the JSON structure to convert
	 * @return the Java equivalent
	 */
	public static Object fromJsonStruct(Object jsonStruct) {
		if (jsonStruct instanceof JSONObject)
			return mapFromJsonObject((JSONObject) jsonStruct);
		if (jsonStruct instanceof JSONArray)
			return listFromJsonArray((JSONArray) jsonStruct);
		if (jsonStruct instanceof String)
			return jsonStruct;
		throw new IllegalArgumentException("Cannot convert " + jsonStruct.getClass().getSimpleName() + " from JSON- to Java object");
	}

}
