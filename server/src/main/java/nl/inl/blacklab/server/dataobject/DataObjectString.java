package nl.inl.blacklab.server.dataobject;

import java.io.IOException;
import java.io.Writer;

import nl.inl.util.StringUtil;

/**
 * A string value.
 */
public class DataObjectString extends DataObject {

	String value;

	public DataObjectString(String value) {
		this.value = value;
	}

	@Override
	public void serialize(Writer out, DataFormat fmt, boolean prettyPrint, int depth) throws IOException {
		switch(fmt) {
		case JSON:
			if (value == null)
				out.append("null");
			else
				out.append("\"").append(StringUtil.escapeDoubleQuotedString(value)).append("\"");
			break;
		case XML:
			if (value == null)
				out.append("(null)");
			else
				out.append(StringUtil.escapeXmlChars(value));
			break;
		}
	}

	@Override
	public boolean isSimple() {
		return true;
	}

	public boolean isEmpty() {
		return value == null || value.length() == 0;
	}

}
