package nl.inl.blacklab.server.dataobject;

import java.io.IOException;
import java.io.Writer;

/**
 * A plain value that be output as-is, not converted into JSON or XML.
 */
public class DataObjectPlain extends DataObject {

	String value;

	/** If this is the top-level object we're returning,
	 *  should we add a root element or leave this as-is?
	 *  (i.e. original XML input documents shouldn't get additional
	 *   root element around it) */
	boolean addRootElement = true;

	public DataObjectPlain(String value, DataFormat type) {
		this.value = value;
	}

	public DataObjectPlain(String value) {
		this(value, DataFormat.XML);
	}

	@Override
	public void serialize(Writer out, DataFormat fmt, boolean prettyPrint, int depth) throws IOException {
		out.append(value);
	}

	@Override
	public boolean isSimple() {
		return true;
	}

	public boolean shouldAddRootElement() {
		return addRootElement;
	}

	public void setAddRootElement(boolean addRootElement) {
		this.addRootElement = addRootElement;
	}

}
