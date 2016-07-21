package nl.inl.blacklab.server.dataobject;

import java.io.IOException;
import java.io.Writer;
import java.util.Map;

import nl.inl.util.StringUtil;

/**
 * A collection of names mapping to DataObjects.
 *
 * The XML representation puts the keys in attribute values.
 */
public class DataObjectMapAttribute extends DataObjectMapElement {

	/** Element name to use for map elements */
	private String xmlElementName;

	/** Element name to use for key attributes */
	private String xmlAttributeName;

	public DataObjectMapAttribute(String xmlElementName, String xmlAttributeName) {
		super();
		this.xmlElementName = xmlElementName;
		this.xmlAttributeName = xmlAttributeName;
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
				out.append("<").append(xmlElementName).append(" ").append(xmlAttributeName).append("=\"").append(StringUtil.escapeXmlChars(key)).append("\">");
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
}
