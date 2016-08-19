package nl.inl.blacklab.server.datastream;

import java.io.PrintWriter;
import java.util.List;

import nl.inl.util.StringUtil;

/**
 * Class to stream out JSON data.
 *
 * This is faster than building a full object tree first.
 * Intended to replace the DataObject classes.
 */
public class DataStreamJson extends DataStream {

	/** JSONP callback function name, or null for none */
	String jsonpCallback;

	boolean isJsonp = false;

	/** First entry in map/list: don't print separator */
	boolean firstEntry = true;

	public DataStreamJson(PrintWriter out, boolean prettyPrint, String jsonpCallback) {
		super(out, prettyPrint);
		this.jsonpCallback = jsonpCallback;
		isJsonp = jsonpCallback != null && jsonpCallback.length() > 0;
	}

	DataStream openbl(String str) {
		firstEntry = true;
		return print(str).upindent();
	}

	DataStream closebl(String str) {
		firstEntry = false;
		return downindent().newlineIndent().print(str);
	}

	@Override
	public DataStream startDocument(String rootEl) {
		if (isJsonp) {
			print(jsonpCallback).print("(");
		}
		return this;
	}

	@Override
	public DataStream endDocument(String rootEl) {
		if (isJsonp) {
			print(");");
		}
		return this;
	}

	@Override
	public DataStream startList() {
		return openbl("[");
	}

	@Override
	public DataStream endList() {
		return closebl("]");
	}

	@Override
	public DataStream startItem(String name) {
		return optSep().newlineIndent();
	}

	@Override
	public DataStream endItem() {
		return this;
	}

	@Override
	public DataStream startMap() {
		return openbl("{");
	}

	@Override
	public DataStream endMap() {
		return closebl("}");
	}

	DataStream optSep() {
		if (!firstEntry)
			return print(",");
		firstEntry = false;
		return this;
	}

	@Override
	public DataStream startEntry(String key) {
		return optSep().newlineIndent().print("\"").print(StringUtil.escapeDoubleQuotedString(key)).print("\":").space();
	}

	@Override
	public DataStream endEntry() {
		return this;
	}

	@Override
	public DataStream startAttrEntry(String elementName, String attrName, String key) {
		return startEntry(key);
	}

	@Override
	public DataStream startAttrEntry(String elementName, String attrName, int key) {
		return startEntry(Integer.toString(key));
	}

	@Override
	public DataStream endAttrEntry() {
		return this;
	}

	@Override
	public DataStream contextList(List<String> names, List<String> values) {
		openbl("{");
		int valuesPerWord = names.size();
		int numberOfWords = values.size() / valuesPerWord;
		for (int k = 0; k < names.size(); k++) {
			optSep();
			newlineIndent();
			String name = names.get(k);
			print("\"").print(StringUtil.escapeDoubleQuotedString(name)).print("\":[");
			for (int i = 0; i < numberOfWords; i++) {
				if (i > 0)
					print(",");
				int vIndex = i * valuesPerWord;
				String value = values.get(vIndex + k);
				print("\"").print(StringUtil.escapeDoubleQuotedString(value)).print("\"");
			}
			out.append("]");
		}
		return closebl("}");
	}

	@Override
	public DataStream value(String value) {
		return value == null ? print("null") :
			print("\"").print(StringUtil.escapeDoubleQuotedString(value)).print("\"");
	}

	@Override
	public DataStream value(long value) {
		return print(value);
	}

	@Override
	public DataStream value(double value) {
		return print(value);
	}

	@Override
	public DataStream value(boolean value) {
		return print(value);
	}

}
