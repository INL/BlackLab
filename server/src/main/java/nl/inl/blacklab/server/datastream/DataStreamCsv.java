package nl.inl.blacklab.server.datastream;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;

import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.server.exceptions.BadRequest;

/**
 * Disables all functions except for {@link DataStreamAbstract#plain(String)} Not ideal,
 * but required for certain outputs from requesthandlers. Since the main
 * requesthandler always adds some padding data, and we need to suppress this
 * for some data types.
 */
public class DataStreamCsv extends DataStreamAbstract {

    private static DataStreamAbstract csvNotSupported() {
        throw new BadRequest("CSV_NOT_SUPPORTED", "This request does not support CSV output.");
    }

    @Override
    public void error(String code, String msg, Throwable e) {
        plain(code + "\n");
        plain(msg + "\n");
        if (e != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            plain(sw.toString());
        }
    }

    public DataStreamCsv(PrintWriter out, boolean prettyPrint) {
        super(out, prettyPrint);
    }

    @Override
    public DataStream startDocument(String rootEl) {
        return this;
    }

    @Override
    public DataStream endDocument() {
        return this;
    }

    @Override
    DataStreamAbstract pretty(String str) { return csvNotSupported(); }

    @Override
    public DataStream startList() { return csvNotSupported(); }

    @Override
    public DataStream endList() { return csvNotSupported(); }

    @Override
    public DataStream startItem(String name) { return csvNotSupported(); }

    @Override
    public DataStream endItem() { return csvNotSupported(); }

    @Override
    public DataStream startMap() { return csvNotSupported(); }

    @Override
    public DataStream endMap() { return csvNotSupported(); }

    @Override
    public DataStream startEntry(String key) { return csvNotSupported(); }

    @Override
    public DataStream endEntry() { return csvNotSupported(); }

    @Override
    public DataStream startAttrEntry(String elementName, String attrName, String key) { return csvNotSupported(); }

    @Override
    public DataStream startAttrEntry(String elementName, String attrName, int key) { return csvNotSupported(); }

    @Override
    public DataStream endAttrEntry() { return csvNotSupported(); }

    @Override
    public DataStream contextList(List<Annotation> annotations, Collection<Annotation> annotationsToList,
            List<String> values) {
        return csvNotSupported();
    }

    @Override
    public DataStream value(String value) { return csvNotSupported(); }

    @Override
    public DataStream value(long value) { return csvNotSupported(); }

    @Override
    public DataStream value(double value) { return csvNotSupported(); }

    @Override
    public DataStream value(boolean value) { return csvNotSupported(); }

}
