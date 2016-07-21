package nl.inl.blacklab.server.dataobject;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * A collection of integers mapping to DataObjects.
 */
public class DataObjectMapInt extends DataObject {

	Map<Integer, DataObject> map = new LinkedHashMap<>();

	String xmlElementName;

	String xmlIdAttributeName;

	public String getElementName() {
		return xmlElementName;
	}

	public String getIdAttributeName() {
		return xmlIdAttributeName;
	}

	public DataObjectMapInt(String xmlElementName, String xmlIdAttributeName) {
		this.xmlElementName = xmlElementName;
		this.xmlIdAttributeName = xmlIdAttributeName;
	}

	@Override
	public void serialize(Writer out, DataFormat fmt, boolean prettyPrint, int depth) throws IOException {
		switch (fmt) {
		case JSON:
			out.append("{");
			break;
		case XML:
			break;
		}
		boolean first = true;
		depth++;
		for (Map.Entry<Integer, DataObject> e: map.entrySet()) {
			int key = e.getKey();
			DataObject value = e.getValue();
			switch (fmt) {
			case JSON:
				if (!first)
					out.append(",");
				if (prettyPrint) {
					out.append("\n");
					indent(out, depth);
				}
				out.append("\"").append(Integer.toString(key)).append("\":");
				if (prettyPrint)
					out.append(" ");
				value.serialize(out, fmt, prettyPrint, depth);
				break;
			case XML:
				if (prettyPrint)
					indent(out, depth);
				out.append("<").append(xmlElementName).append(" ").
					append(xmlIdAttributeName).append("=\"").append(Integer.toString(key)).append("\">");
				if (prettyPrint && !value.isSimple()) {
					out.append("\n");
				}
				value.serialize(out, fmt, prettyPrint, depth);
				if (prettyPrint && !value.isSimple()) {
					indent(out, depth);
				}
				out.append("</").append(xmlElementName).append(">");
				if (prettyPrint)
					out.append("\n");
				break;
			}
			first = false;
		}
		depth--;
		switch (fmt) {
		case JSON:
			if (prettyPrint) {
				out.append("\n");
				indent(out, depth);
			}
			out.append("}");
			break;
		case XML:
			break;
		}
	}

	public int size() {
		return map.size();
	}

	public boolean isEmpty() {
		return map.isEmpty();
	}

	public boolean containsKey(Object key) {
		return map.containsKey(key);
	}

	public boolean containsValue(Object value) {
		return map.containsValue(value);
	}

	public DataObject get(Object key) {
		return map.get(key);
	}

	public DataObject put(int key, DataObject value) {
		return map.put(key, value);
	}

	public DataObject put(int key, String value) {
		return map.put(key, DataObject.from(value));
	}

	public DataObject put(int key, int value) {
		return map.put(key, DataObject.from(value));
	}

	public DataObject put(int key, long value) {
		return map.put(key, DataObject.from(value));
	}

	public DataObject put(int key, double value) {
		return map.put(key, DataObject.from(value));
	}

	public DataObject put(int key, boolean value) {
		return map.put(key, DataObject.from(value));
	}

	public DataObject remove(int key) {
		return map.remove(key);
	}

	public void putAll(Map<? extends Integer, ? extends DataObject> m) {
		map.putAll(m);
	}

	public void clear() {
		map.clear();
	}

	public Set<Integer> keySet() {
		return map.keySet();
	}

	public Collection<DataObject> values() {
		return map.values();
	}

	public Set<Entry<Integer, DataObject>> entrySet() {
		return map.entrySet();
	}

	@Override
	public boolean equals(Object o) {
		return map.equals(o);
	}

	@Override
	public int hashCode() {
		return map.hashCode();
	}

	@Override
	public boolean isSimple() {
		return false;
	}

}
