package nl.inl.blacklab.server.dataobject;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import nl.inl.util.StringUtil;

/**
 * A list of KWIC context values.
 */
public class DataObjectContextList extends DataObject {

	List<String> names = new ArrayList<>();

	List<String> values = new ArrayList<>();

	public DataObjectContextList(List<String> names, List<String> values) {
		this.names = names;
		this.values = values;
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
		int valuesPerWord = names.size();
		int numberOfWords = values.size() / valuesPerWord;
		if (fmt == DataFormat.JSON) {
			for (int k = 0; k < names.size(); k++) {
				if (!first)
					out.append(",");
				if (prettyPrint) {
					out.append("\n");
					indent(out, depth);
				}
				String name = names.get(k);
				out.append("\"").append(StringUtil.escapeDoubleQuotedString(name)).append("\":[");
				for (int i = 0; i < numberOfWords; i++) {
					if (i > 0)
						out.append(",");
					int vIndex = i * valuesPerWord;
					String value = values.get(vIndex + k);
					out.append("\"").append(StringUtil.escapeDoubleQuotedString(value)).append("\"");
				}
				out.append("]");
				first = false;
			}
		} else {
			for (int i = 0; i < numberOfWords; i++) {
				int vIndex = i * valuesPerWord;
				int j = 0;
				if (prettyPrint)
					indent(out, depth);
				out.append(StringUtil.escapeXmlChars(values.get(vIndex))); // punct
				out.append("<w");
				for (int k = 1; k < names.size() - 1; k++) {
					String name = names.get(k);
					String value = values.get(vIndex + 1 + j);
					out.append(" ").append(name).append("=\"").append(StringUtil.escapeXmlChars(value)).append("\"");
					j++;
				}
				out.append(">");
				out.append(StringUtil.escapeXmlChars(values.get(vIndex + 1 + j))); // word
				out.append("</w>");
				if (prettyPrint)
					out.append("\n");
				first = false;
			}
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

	@Override
	public boolean isSimple() {
		return false;
	}


}
