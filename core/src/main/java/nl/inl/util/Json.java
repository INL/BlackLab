package nl.inl.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;
import java.nio.charset.Charset;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Supports reading/writing commented-JSON files.
 */
public class Json {

	/**
	 * Write a JSON object to a file.
	 * @param data the object to write
	 * @param file the file to write to
	 */
	public static void write(JSONObject data, File file) {
		try (PrintWriter out = FileUtil.openForWriting(file)) {
			out.print(data.toString(2));
		}
	}

	/**
	 * Read a JSON object from a file.
	 *
	 * The data may be commented using Java-style end-of-line comments (//).
	 *
	 * @param file the file to read from
	 * @return the JSON object read
	 * @throws JSONException if the data read was not valid commented-JSON
	 * @throws IOException on I/O error
	 */
	public static JSONObject read(File file) throws JSONException, IOException {
		try (BufferedReader reader = FileUtil.openForReading(file)) {
			return new JSONObject(readFileStripLineComments(reader));
		}
	}


	/**
	 * Read a JSON object from a stream.
	 *
	 * The data may be commented using Java-style end-of-line comments (//).
	 *
	 * @param is the stream to read from
	 * @param encoding character encoding to use
	 * @return the JSON object read
	 * @throws JSONException if the data read was not valid commented-JSON
	 * @throws IOException on I/O error
	 */
	public static JSONObject read(InputStream is, Charset encoding) throws JSONException, IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is, encoding));
		return new JSONObject(readFileStripLineComments(reader));
	}
	/**
	 * Read a JSON object from a stream.
	 *
	 * The data may be commented using Java-style end-of-line comments (//).
	 *
	 * @param is the stream to read from
	 * @param encoding character encoding to use
	 * @return the JSON object read
	 * @throws JSONException if the data read was not valid commented-JSON
	 * @throws IOException on I/O error
	 */
	public static JSONObject read(InputStream is, String encoding) throws JSONException, IOException {
		return read(is, Charset.forName(encoding));
	}

	/**
	 * Read a JSON object from a Reader.
	 *
	 * The data may be commented using Java-style end-of-line comments (//).
	 *
	 * @param reader where to read from
	 * @return the JSON object read
	 * @throws JSONException if the data read was not valid commented-JSON
	 * @throws IOException on I/O error
	 */
	public static JSONObject read(Reader reader) throws JSONException, IOException {
		BufferedReader buffReader;
		if (reader instanceof BufferedReader)
			buffReader = (BufferedReader) reader;
		else
			buffReader = new BufferedReader(reader);
		return new JSONObject(readFileStripLineComments(buffReader));
	}

	private static String readFileStripLineComments(BufferedReader reader) throws IOException {
		StringBuilder b = new StringBuilder();
		while (true) {
			String line = reader.readLine();
			if (line == null)
				break;
			int n = line.length();
			boolean inString = false;
			boolean done = false;
			for (int i = 0; i < n && !done; i++) {
				switch(line.charAt(i)) {
				case '\\':
					if (inString) {
						// Escape: skip the next character
						i++;
					}
					break;
				case '"':
					// Keep track of whether we're in a string or not
					inString = inString ? false : true;
					break;
				case '/':
					// Start of end-of-line comment?
					if (!inString && i < n - 1 && line.charAt(i + 1) == '/') {
						// Yes, strip and break from loop
						line = line.substring(0, i).trim();
						done = true;
					}
					break;
				}
			}
			b.append(line).append("\n");
		}
		return b.toString();
	}

	public static JSONObject object(Object... keyValues) {
		JSONObject obj = new JSONObject();
		if (keyValues.length % 2 != 0) {
			throw new IllegalArgumentException("Odd number of parameters");
		}
		for (int i = 0; i < keyValues.length; i += 2) {
			if (!(keyValues[i] instanceof String)) {
				throw new IllegalArgumentException("Non-string key");
			}
			String key = (String)keyValues[i];
			Object value = keyValues[i + 1];
			obj.put(key, value);
		}
		return obj;
	}

	/**
	 * Get or create a JSONObject child of the specified parent.
	 *
	 * @param parent parent node to get the object from
	 * @param name name of the JSONObject to get
	 * @return the object
	 * @throws RuntimeException if a non-JSONObject child with this name exists
	 */
	public static JSONObject getObject(JSONObject parent, String name) {
		Object object = null;
		if (parent.has(name))
			object = parent.get(name);
		if (object != null) {
			if (!(object instanceof JSONObject))
				throw new IllegalArgumentException("Not a JSONObject: " + name);
		} else {
			object = new JSONObject();
			parent.put(name, object);
		}
		return (JSONObject) object;
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
	public static String getString(JSONObject parent, String name, String defVal) {
		if (parent.has(name))
			return parent.getString(name);
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
	public static boolean getBoolean(JSONObject parent, String name, boolean defVal) {
		if (parent.has(name))
			return parent.getBoolean(name);
		return defVal;
	}

}
