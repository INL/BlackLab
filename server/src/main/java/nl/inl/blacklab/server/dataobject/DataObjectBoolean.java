package nl.inl.blacklab.server.dataobject;

import java.io.IOException;
import java.io.Writer;

/**
 * An int or a double value.
 */
public class DataObjectBoolean extends DataObject {

	public static DataObjectBoolean TRUE = new DataObjectBoolean(true);

	public static DataObjectBoolean FALSE = new DataObjectBoolean(false);

	boolean value;

	private DataObjectBoolean(boolean value) {
		this.value = value;
	}

	@Override
	public void serialize(Writer out, DataFormat fmt, boolean prettyPrint, int depth) throws IOException {
		out.append(value ? "true" : "false");
	}

	@Override
	public boolean isSimple() {
		return true;
	}

}
