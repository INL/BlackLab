package nl.inl.blacklab.datastream;

import java.io.PrintWriter;
import java.util.List;

import nl.inl.blacklab.server.dataobject.DataFormat;

/**
 * Class to stream out XML or JSON data.
 *
 * This is faster than building a full object tree first.
 * Intended to replace the DataObject classes.
 */
public abstract class DataStream {

	public static DataStream create(DataFormat format, PrintWriter out, boolean prettyPrint, String jsonpCallback) {
		if (format == DataFormat.JSON)
			return new DataStreamJson(out, prettyPrint, jsonpCallback);
		return new DataStreamXml(out, prettyPrint);
	}

	PrintWriter out;

	int indent = 0;

	boolean prettyPrint;

	boolean prettyPrintPref;

	public DataStream(PrintWriter out, boolean prettyPrint) {
		this.out = out;
		this.prettyPrintPref = this.prettyPrint = prettyPrint;
	}

	DataStream print(String str) {
		out.print(str);
		return this;
	}

	DataStream print(long value) {
		out.print(value);
		return this;
	}

	DataStream print(double value) {
		out.print(value);
		return this;
	}

	DataStream print(boolean value) {
		out.print(value ? "true" : "false");
		return this;
	}

	DataStream pretty(String str) {
		if (prettyPrint)
			print(str);
		return this;
	}

	DataStream upindent() {
		indent++;
		return this;
	}

	DataStream downindent() {
		indent--;
		return this;
	}

	DataStream indent() {
		if (prettyPrint) {
			for (int i = 0; i < indent; i++) {
				print("  ");
			}
		}
		return this;
	}

	DataStream newlineIndent() {
		return newline().indent();
	}

	DataStream newline() {
		return pretty("\n");
	}

	DataStream space() {
		return pretty(" ");
	}

	DataStream endCompact() {
		prettyPrint = prettyPrintPref;
		return this;
	}

	DataStream startCompact() {
		prettyPrint = false;
		return this;
	}

	public abstract DataStream startDocument(String rootEl);

	public abstract DataStream endDocument();


	public abstract DataStream startList();

	public abstract DataStream endList();

	public abstract DataStream startItem(String name);

	public abstract DataStream endItem();

	public DataStream item(String name, String value) {
		return startItem(name).value(value).endItem();
	}

	public DataStream item(String name, int value) {
		return startItem(name).value(value).endItem();
	}

	public DataStream item(String name, double value) {
		return startItem(name).value(value).endItem();
	}

	public DataStream item(String name, boolean value) {
		return startItem(name).value(value).endItem();
	}


	public abstract DataStream startMap();

	public abstract DataStream endMap();

	public abstract DataStream startEntry(String key);

	public abstract DataStream endEntry();

	public DataStream entry(String key, String value) {
		return startEntry(key).value(value).endEntry();
	}

	public DataStream entry(String key, int value) {
		return startEntry(key).value(value).endEntry();
	}

	public DataStream entry(String key, double value) {
		return startEntry(key).value(value).endEntry();
	}

	public DataStream entry(String key, boolean value) {
		return startEntry(key).value(value).endEntry();
	}

	public DataStream attrEntry(String elementName, String attrName, String key, String value) {
		return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
	}

	public DataStream attrEntry(String elementName, String attrName, String key, int value) {
		return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
	}

	public DataStream attrEntry(String elementName, String attrName, String key, double value) {
		return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
	}

	public DataStream attrEntry(String elementName, String attrName, String key, boolean value) {
		return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
	}

	public abstract DataStream startAttrEntry(String elementName, String attrName, String key);

	public abstract DataStream startAttrEntry(String elementName, String attrName, int key);

	public abstract DataStream endAttrEntry();

	public abstract DataStream contextList(List<String> names, List<String> values);


	public abstract DataStream value(String value);

	public abstract DataStream value(long value);

	public abstract DataStream value(double value);

	public abstract DataStream value(boolean value);

	public DataStream plain(String value) {
		return print(value);
	}

	public DataStream errorResponse(String code, String message) {
		return startMap()
			.startEntry("error")
				.startMap()
					.entry("code", code)
					.entry("message", message)
				.endMap()
			.endMap();
	}

	public static void main(String[] args) {
		PrintWriter out = new PrintWriter(System.out);

		DataStream test;
		test = new DataStreamXml(out, true);
		go(test, out);
		test = new DataStreamXml(out, false);
		go(test, out);
		test = new DataStreamJson(out, true, null);
		go(test, out);
//		test = new DataStreamJson(System.out, false, null);
//		go(test, out);
		test = new DataStreamJson(out, false, "myJsonCallback");
		go(test, out);

		out.flush();
	}

	private static void go(DataStream test, PrintWriter out) {
		test
			.startDocument("test")
				.entry("fun", true)
				.startEntry("cats")
					.startMap()
						.startAttrEntry("cat", "name", "Sylvie")
							.startList()
								.item("place", "Voorschoten")
							.endList()
						.endEntry()
						.startAttrEntry("cat", "name", "Jelmer")
							.startList()
								.item("place", "Haarlem")
								.item("place", "Leiden")
								.item("place", "Haarlem")
							.endList()
						.endEntry()
						.entry("test", "bla")
						.attrEntry("test", "attr", "key", "value")
					.endMap()
				.endEntry()
			.endDocument();
		out.println("");
	}

}
