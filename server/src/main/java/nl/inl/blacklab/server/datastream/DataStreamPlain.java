package nl.inl.blacklab.server.datastream;

import java.io.PrintWriter;
import java.util.List;

/**
 * Disables all functions except for {@link DataStream#plain(String)}
 * Not ideal, but required for certain outputs from requesthandlers. Since the main requesthandler always adds some padding data, and we need to suppress this for some data types.
 */
public class DataStreamPlain extends DataStream {

	public DataStreamPlain(PrintWriter out, boolean prettyPrint) {
		super(out, prettyPrint);
	}

	@Override
	public DataStream startDocument(String rootEl) {
		return this;
	}

	@Override
	public DataStream endDocument(String rootEl) {
		return this;
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
	public DataStream startItem(String name) {
		return this;
	}

	@Override
	public DataStream endItem() {
		return this;
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
	public DataStream startEntry(String key) {
		return this;
	}

	@Override
	public DataStream endEntry() {
		return this;
	}

	@Override
	public DataStream startAttrEntry(String elementName, String attrName, String key) {
		return this;
	}

	@Override
	public DataStream startAttrEntry(String elementName, String attrName, int key) {
		return this;
	}

	@Override
	public DataStream endAttrEntry() {
		return this;
	}

	@Override
	public DataStream contextList(List<String> names, List<String> values) {
		return this;
	}

	@Override
	public DataStream value(String value) {
		return this;
	}

	@Override
	public DataStream value(long value) {
		return this;
	}

	@Override
	public DataStream value(double value) {
		return this;
	}

	@Override
	public DataStream value(boolean value) {
		return this;
	}

}
