package nl.inl.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.Reader;

import nl.inl.util.json.JSONException;
import nl.inl.util.json.JSONObject;

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
		PrintWriter out = FileUtil.openForWriting(file);
		try {
			out.print(data.toString(2));
		} finally {
			out.close();
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
		BufferedReader reader = FileUtil.openForReading(file);
		try {
			return new JSONObject(readFileStripLineComments(reader));
		} finally {
			reader.close();
		}
	}

	/**
	 * Read a JSON object from a stream.
	 *
	 * The data may be commented using Java-style end-of-line comments (//).
	 *
	 * @param is the stream to read from
	 * @return the JSON object read
	 * @throws JSONException if the data read was not valid commented-JSON
	 * @throws IOException on I/O error
	 */
	public static JSONObject read(InputStream is) throws JSONException, IOException {
		BufferedReader reader = IoUtil.makeBuffered(new InputStreamReader(is));
		return new JSONObject(readFileStripLineComments(reader));
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
		BufferedReader buffReader = IoUtil.makeBuffered(reader);
		return new JSONObject(readFileStripLineComments(buffReader));
	}

	private static String readFileStripLineComments(BufferedReader reader) throws IOException {
		StringBuilder b = new StringBuilder();
		while (true) {
			String line = reader.readLine();
			if (line == null)
				break;
			line = line.replaceAll("//.+$", "").trim();
			b.append(line).append("\n");
		}
		return b.toString();
	}


}
