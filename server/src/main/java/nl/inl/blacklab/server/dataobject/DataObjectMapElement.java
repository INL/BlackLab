package nl.inl.blacklab.server.dataobject;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import nl.inl.util.StringUtil;

import java.util.Set;

/**
 * A collection of names mapping to DataObjects.
 *
 * The XML representation uses the key names as element names.
 */
public class DataObjectMapElement extends DataObject {

	Map<String, DataObject> map = new LinkedHashMap<>();

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
		for (Map.Entry<String, DataObject> e: map.entrySet()) {
			String key = e.getKey();
			DataObject value = e.getValue();
			switch (fmt) {
			case JSON:
				if (!first)
					out.append(",");
				if (prettyPrint) {
					out.append("\n");
					indent(out, depth);
				}
				out.append("\"").append(StringUtil.escapeDoubleQuotedString(key)).append("\":");
				if (prettyPrint)
					out.append(" ");
				value.serialize(out, fmt, prettyPrint, depth);
				break;
			case XML:
				if (prettyPrint)
					indent(out, depth);
				out.append("<").append(key).append(">");
				if (prettyPrint && !value.isSimple()) {
					out.append("\n");
				}
				value.serialize(out, fmt, prettyPrint, depth);
				if (prettyPrint && !value.isSimple()) {
					indent(out, depth);
				}
				out.append("</").append(key).append(">");
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

	public DataObject put(String key, DataObject value) {
		return map.put(key, value);
	}

	public DataObject put(String key, String value) {
		return map.put(key, DataObject.from(value));
	}

	public DataObject put(String key, int value) {
		return map.put(key, DataObject.from(value));
	}

	public DataObject put(String key, long value) {
		return map.put(key, DataObject.from(value));
	}

	public DataObject put(String key, double value) {
		return map.put(key, DataObject.from(value));
	}

	public DataObject put(String key, boolean value) {
		return map.put(key, DataObject.from(value));
	}

	public DataObject remove(String key) {
		return map.remove(key);
	}

	public void putAll(Map<? extends String, ? extends DataObject> m) {
		map.putAll(m);
	}

	public void clear() {
		map.clear();
	}

	public Set<String> keySet() {
		return map.keySet();
	}

	public Collection<DataObject> values() {
		return map.values();
	}

	public Set<Entry<String, DataObject>> entrySet() {
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

	/**
	 * Remove map keys with empty values anywhere inside this object.
	 */
	@Override
	public void removeEmptyMapValues() {
		List<String> toRemove = new ArrayList<>();
		for (Map.Entry<String, DataObject> e: map.entrySet()) {
			DataObject value = e.getValue();
			value.removeEmptyMapValues();
			if (value instanceof DataObjectString && ((DataObjectString) value).isEmpty()) {
				toRemove.add(e.getKey());
			}
		}
		for (String key: toRemove) {
			map.remove(key);
		}
	}
}
