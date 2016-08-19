package nl.inl.blacklab.server.datastream;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import nl.inl.util.StringUtil;

/**
 * Class to stream out XML data.
 *
 * This is faster than building a full object tree first.
 * Intended to replace the DataObject classes.
 */
public class DataStreamXml extends DataStream {

	List<String> tagStack = new ArrayList<>();

	public DataStreamXml(PrintWriter out, boolean prettyPrint) {
		super(out, prettyPrint);
	}

	private DataStream startOpenEl(String name) {
		tagStack.add(name);
		return indent().print("<").print(name);
	}

	private DataStream attr(String key, String value) {
		return print(" ").print(key).print("=\"").print(StringUtil.escapeXmlChars(value)).print("\"");
	}

	private DataStream endOpenEl() {
		return print(">").upindent().newline();
	}

	private DataStream openEl(String name) {
		startOpenEl(name);
		return endOpenEl();
	}

	private DataStream closeEl() {
		String name = tagStack.remove(tagStack.size() - 1);
		return downindent().indent().print("</").print(name).print(">").newline();
	}

	@Override
	public DataStream startDocument(String rootEl) {
		if (rootEl == null)
			return this;
		print("<?xml version=\"1.0\" encoding=\"utf-8\" ?>").newline();
		startOpenEl(rootEl);
		return endOpenEl();
	}

	@Override
	public DataStream endDocument(String rootEl) {
		if (rootEl == null)
			return this;
		startCompact();
		return closeEl().endCompact();
	}

	@Override
	public DataStream startList() {
		return this;
	}

	@Override
	public DataStream endList() {
		return this;
	}

	@Override
	public DataStream item(String name, String value) {
		return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
	}

	@Override
	public DataStream item(String name, int value) {
		return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
	}

	@Override
	public DataStream item(String name, double value) {
		return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
	}

	@Override
	public DataStream item(String name, boolean value) {
		return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
	}

	@Override
	public DataStream startItem(String name) {
		return openEl(name);
	}

	@Override
	public DataStream endItem() {
		return closeEl();
	}

	@Override
	public DataStream startMap() {
		return this;
	}

	@Override
	public DataStream endMap() {
		return this;
	}

	@Override
	public DataStream entry(String key, String value) {
		return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
	}

	@Override
	public DataStream entry(String key, int value) {
		return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
	}

	@Override
	public DataStream entry(String key, double value) {
		return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
	}

	@Override
	public DataStream entry(String key, boolean value) {
		return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
	}

	@Override
	public DataStream startEntry(String key) {
		return openEl(key);
	}

	@Override
	public DataStream endEntry() {
		return closeEl();
	}

	@Override
	public DataStream attrEntry(String elementName, String attrName, String key, String value) {
		return indent().startCompact()
			.startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
			.endCompact().newline();
	}

	@Override
	public DataStream attrEntry(String elementName, String attrName, String key, int value) {
		return indent().startCompact()
				.startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
				.endCompact().newline();
	}

	@Override
	public DataStream attrEntry(String elementName, String attrName, String key, double value) {
		return indent().startCompact()
				.startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
				.endCompact().newline();
	}

	@Override
	public DataStream attrEntry(String elementName, String attrName, String key, boolean value) {
		return indent().startCompact()
				.startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
				.endCompact().newline();
	}

	@Override
	public DataStream startAttrEntry(String elementName, String attrName,
			String key) {
		startOpenEl(elementName);
		attr(attrName, key);
		return endOpenEl();
	}

	@Override
	public DataStream startAttrEntry(String elementName, String attrName,
			int key) {
		return startEntry(Integer.toString(key));
	}

	@Override
	public DataStream endAttrEntry() {
		return closeEl();
	}

	@Override
	public DataStream contextList(List<String> names, List<String> values) {
		upindent();
		int valuesPerWord = names.size();
		int numberOfWords = values.size() / valuesPerWord;
		for (int i = 0; i < numberOfWords; i++) {
			int vIndex = i * valuesPerWord;
			int j = 0;
			indent();
			print(StringUtil.escapeXmlChars(values.get(vIndex)));
			print("<w");
			for (int k = 1; k < names.size() - 1; k++) {
				String name = names.get(k);
				String value = values.get(vIndex + 1 + j);
				print(" ").print(name).print("=\"").print(StringUtil.escapeXmlChars(value)).print("\"");
				j++;
			}
			print(">");
			print(StringUtil.escapeXmlChars(values.get(vIndex + 1 + j)));
			print("</w>");
			newline();
		}
		return downindent();
	}

	@Override
	public DataStream value(String value) {
		indent();
		if (value == null)
			print("(null)");
		else
			print(StringUtil.escapeXmlChars(value));
		return newline();
	}

	@Override
	public DataStream value(long value) {
		return indent().print(value).newline();
	}

	@Override
	public DataStream value(double value) {
		return indent().print(value).newline();
	}

	@Override
	public DataStream value(boolean value) {
		return indent().print(value).newline();
	}

}
