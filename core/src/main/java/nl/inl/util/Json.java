package nl.inl.util;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

/**
 * Supports reading/writing JSON and YAML files.
 *
 * NOTE: C++ style // comments are allowed in Json files, which is a non-standard extension.
 *
 */
public class Json {

    static private JsonFactory jsonFactory;

    static private ObjectMapper jsonObjectMapper;

    static private JsonFactory yamlFactory;

    static private ObjectMapper yamlObjectMapper;

    static {
        initObjectMappers();
    }

    private static void initObjectMappers() {
        jsonFactory = new JsonFactory();
        jsonFactory.enable(JsonParser.Feature.ALLOW_COMMENTS);
        jsonObjectMapper = new ObjectMapper(jsonFactory);

        yamlFactory = new YAMLFactory();
        yamlObjectMapper = new ObjectMapper(yamlFactory);
    }

    public static ObjectMapper getJsonObjectMapper() {
        return jsonObjectMapper;
    }

    public static ObjectMapper getYamlObjectMapper() {
        return yamlObjectMapper;
    }

//	/**
//	 * Write a JSON object to a file.
//	 * @param data the object to write
//	 * @param file the file to write to
//	 * @throws IOException
//	 * @throws JsonMappingException
//	 * @throws JsonGenerationException
//	 */
//	public static void write(JsonNode data, File file) throws JsonGenerationException, JsonMappingException, IOException {
//		try (PrintWriter out = FileUtil.openForWriting(file)) {
//		    jsonObjectMapper.writeValue(out, data);
//		}
//	}
//
//	/**
//	 * Read a JSON object from a file.
//	 *
//	 * The data may be commented using Java-style end-of-line comments (//).
//	 *
//	 * @param file the file to read from
//	 * @return the JSON object read
//	 * @throws JsonProcessingException if the data read was not valid commented-JSON
//	 * @throws IOException on I/O error
//	 */
//	public static JsonNode read(File file) throws JsonProcessingException, IOException {
//		try (BufferedReader reader = FileUtil.openForReading(file)) {
//			return jsonObjectMapper.readTree(readFileStripLineComments(reader));
//		}
//	}
//
//	/**
//	 * Read a JSON object from a stream.
//	 *
//	 * The data may be commented using Java-style end-of-line comments (//).
//	 *
//	 * @param is the stream to read from
//	 * @param encoding character encoding to use
//	 * @return the JSON object read
//	 * @throws JsonProcessingException if the data read was not valid commented-JSON
//	 * @throws IOException on I/O error
//	 */
//	public static JsonNode read(InputStream is, Charset encoding) throws JsonProcessingException, IOException {
//		BufferedReader reader = new BufferedReader(new InputStreamReader(is, encoding));
//		return jsonObjectMapper.readTree(readFileStripLineComments(reader));
//	}
//
//	/**
//	 * Read a JSON object from a stream.
//	 *
//	 * The data may be commented using Java-style end-of-line comments (//).
//	 *
//	 * @param is the stream to read from
//	 * @param encoding character encoding to use
//	 * @return the JSON object read
//	 * @throws JsonProcessingException if the data read was not valid commented-JSON
//	 * @throws IOException on I/O error
//	 */
//	public static JsonNode read(InputStream is, String encoding) throws JsonProcessingException, IOException {
//		return read(is, Charset.forName(encoding));
//	}
//
//	/**
//	 * Read a JSON object from a Reader.
//	 *
//	 * The data may be commented using Java-style end-of-line comments (//).
//	 *
//	 * @param reader where to read from
//	 * @return the JSON object read
//	 * @throws JsonProcessingException if the data read was not valid commented-JSON
//	 * @throws IOException on I/O error
//	 */
//	public static JsonNode read(Reader reader) throws JsonProcessingException, IOException {
//		BufferedReader buffReader;
//		if (reader instanceof BufferedReader)
//			buffReader = (BufferedReader) reader;
//		else
//			buffReader = new BufferedReader(reader);
//		return jsonObjectMapper.readTree(readFileStripLineComments(buffReader));
//	}

//	private static String readFileStripLineComments(BufferedReader reader) throws IOException {
//		StringBuilder b = new StringBuilder();
//		while (true) {
//			String line = reader.readLine();
//			if (line == null)
//				break;
//			int n = line.length();
//			boolean inString = false;
//			boolean done = false;
//			for (int i = 0; i < n && !done; i++) {
//				switch(line.charAt(i)) {
//				case '\\':
//					if (inString) {
//						// Escape: skip the next character
//						i++;
//					}
//					break;
//				case '"':
//					// Keep track of whether we're in a string or not
//					inString = inString ? false : true;
//					break;
//				case '/':
//					// Start of end-of-line comment?
//					if (!inString && i < n - 1 && line.charAt(i + 1) == '/') {
//						// Yes, strip and break from loop
//						line = line.substring(0, i).trim();
//						done = true;
//					}
//					break;
//				}
//			}
//			b.append(line).append("\n");
//		}
//		return b.toString();
//	}

//	public static ObjectNode object(Object... keyValues) {
//		JsonNode obj = new JsonNode();
//		if (keyValues.length % 2 != 0) {
//			throw new IllegalArgumentException("Odd number of parameters");
//		}
//		for (int i = 0; i < keyValues.length; i += 2) {
//			if (!(keyValues[i] instanceof String)) {
//				throw new IllegalArgumentException("Non-string key");
//			}
//			String key = (String)keyValues[i];
//			Object value = keyValues[i + 1];
//			obj.put(key, value);
//		}
//		return obj;
//	}

	/**
	 * Get or create a JsonNode child of the specified parent.
	 *
	 * @param parent parent node to get the object from
	 * @param name name of the JsonNode to get
	 * @return the object
	 * @throws RuntimeException if a non-JsonNode child with this name exists
	 */
	public static ObjectNode getObject(ObjectNode parent, String name) {
	    ObjectNode object = null;
		if (parent.has(name))
			object = (ObjectNode)parent.get(name);
		else
			object = parent.putObject(name);
		return object;
	}

	/**
	 * Get a string value from a JSON Object, or substitute a default value
	 * if the key doesn't exist.
	 *
	 * @param parent parent node to get the object from
	 * @param name name of the value to get
	 * @param defVal value to use if key doesn't exist
	 * @return the string value
	 */
	public static String getString(JsonNode parent, String name, String defVal) {
		if (parent.has(name))
			return parent.get(name).textValue();
		return defVal;
	}

	/**
	 * Get a boolean value from a JSON Object, or substitute a default value
	 * if the key doesn't exist.
	 *
	 * @param parent parent node to get the object from
	 * @param name name of the value to get
	 * @param defVal value to use if key doesn't exist
	 * @return the boolean value
	 */
	public static boolean getBoolean(JsonNode parent, String name, boolean defVal) {
		if (parent.has(name))
			return parent.get(name).booleanValue();
		return defVal;
	}

    public static ArrayNode arrayOfStrings(ArrayNode arr, List<String> fields) {
        for (String str: fields) {
            arr.add(str);
        }
        return arr;
    }

    public static List<String> getListOfStrings(JsonNode group, String name) {
        ArrayNode arr = (ArrayNode)group.get(name);
        List<String> result = new ArrayList<>();
        if (arr != null) {
            for (int i = 0; i < arr.size(); i++) {
                result.add(arr.get(i).textValue());
            }
        }
        return result;
    }

}
