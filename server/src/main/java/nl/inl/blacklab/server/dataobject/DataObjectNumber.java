package nl.inl.blacklab.server.dataobject;

import java.io.IOException;
import java.io.Writer;

/**
 * A long integer or a double precision float value.
 */
public class DataObjectNumber extends DataObject {

	double fNum;

	long iNum;

	boolean isInt;

	public DataObjectNumber(long iNum) {
		this.iNum = iNum;
		isInt = true;
	}

	public DataObjectNumber(double fNum) {
		this.fNum = fNum;
		isInt = false;
	}

	@Override
	public void serialize(Writer out, DataFormat fmt, boolean prettyPrint, int depth) throws IOException {
		if (isInt) {
			out.append("" + iNum);
		} else {
			out.append("" + fNum);
		}
	}

	@Override
	public boolean isSimple() {
		return true;
	}

}
