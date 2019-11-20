package nl.inl.blacklab.server.datastream;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;

import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.server.util.ServletUtil;

/**
 * Class to stream out XML or JSON data.
 *
 * This is faster than building a full object tree first. Intended to replace
 * the DataObject classes.
 */
public abstract class DataStream {

    public static DataStream create(DataFormat format, PrintWriter out, boolean prettyPrint, String jsonpCallback) {
        if (format == DataFormat.JSON)
            return new DataStreamJson(out, prettyPrint, jsonpCallback);
        if (format == DataFormat.CSV)
            return new DataStreamPlain(out, prettyPrint);
        return new DataStreamXml(out, prettyPrint);
    }

    /**
     * Stream a simple status response.
     *
     * Status response may indicate success, or e.g. that the server is carrying out
     * the request and will have results later.
     *
     * @param code (string) status code
     * @param msg the message
     * @param checkAgainMs advice for how long to wait before asking again (ms) (if
     *            0, don't include this)
     * @deprecated checkAgainMs will be removed eventually
     */
    @Deprecated
    public void statusObject(String code, String msg, int checkAgainMs) {
        startMap()
                .startEntry("status")
                .startMap()
                .entry("code", code)
                .entry("message", msg);
        if (checkAgainMs != 0)
            entry("checkAgainMs", checkAgainMs);
        endMap()
                .endEntry()
                .endMap();
    }

    /**
     * Construct a simple status response object.
     *
     * Status response may indicate success, or e.g. that the server is carrying out
     * the request and will have results later.
     *
     * @param code (string) status code
     * @param msg the message
     */
    public void statusObject(String code, String msg) {
        statusObject(code, msg, 0);
    }

    /**
     * Construct a simple error response object.
     *
     * @param code (string) error code
     * @param msg the error message
     * @param e if specified, include stack trace
     */
    public void error(String code, String msg, Exception e) {
        startMap()
                .startEntry("error")
                .startMap()
                .entry("code", code)
                .entry("message", msg);
        if (e != null) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            entry("stackTrace", sw.toString());
        }
        endMap()
                .endEntry()
                .endMap();
    }

    /**
     * Construct a simple error response object.
     *
     * @param code (string) error code
     * @param msg the error message
     */
    public void error(String code, String msg) {
        error(code, msg, null);
    }

    public void internalError(Exception e, boolean debugMode, String code) {
        error("INTERNAL_ERROR", ServletUtil.internalErrorMessage(e, debugMode, code), debugMode ? e : null);
    }

    public void internalError(String message, boolean debugMode, String code) {
        error("INTERNAL_ERROR", ServletUtil.internalErrorMessage(message, debugMode, code));
    }

    public void internalError(String code) {
        error("INTERNAL_ERROR", ServletUtil.internalErrorMessage(code));
    }

    protected final PrintWriter out;

    private int indent = 0;

    private boolean prettyPrint;

    private final boolean prettyPrintPref;

    /** Should contextList omit empty properties if possible? */
    protected boolean omitEmptyProperties = false;

    public DataStream(PrintWriter out, boolean prettyPrint) {
        this.out = out;
        this.prettyPrintPref = this.prettyPrint = prettyPrint;
    }

    DataStream print(String str) {
        out.print(str);
        return this;
    }

    DataStream print(long value) {
        out.print(value);
        return this;
    }

    DataStream print(double value) {
        out.print(value);
        return this;
    }

    DataStream print(boolean value) {
        out.print(value ? "true" : "false");
        return this;
    }

    DataStream pretty(String str) {
        if (prettyPrint)
            print(str);
        return this;
    }

    DataStream upindent() {
        indent++;
        return this;
    }

    DataStream downindent() {
        indent--;
        return this;
    }

    DataStream indent() {
        if (prettyPrint) {
            for (int i = 0; i < indent; i++) {
                print("  ");
            }
        }
        return this;
    }

    DataStream newlineIndent() {
        return newline().indent();
    }

    DataStream newline() {
        return pretty("\n");
    }

    DataStream space() {
        return pretty(" ");
    }

    DataStream endCompact() {
        prettyPrint = prettyPrintPref;
        return this;
    }

    DataStream startCompact() {
        prettyPrint = false;
        return this;
    }

    public void outputProlog() {
        // subclasses may override
    }

    public abstract DataStream startDocument(String rootEl);

    public abstract DataStream endDocument(String rootEl);

    public abstract DataStream startList();

    public abstract DataStream endList();

    public abstract DataStream startItem(String name);

    public abstract DataStream endItem();

    public DataStream item(String name, String value) {
        return startItem(name).value(value).endItem();
    }

    public DataStream item(String name, Object value) {
        return startItem(name).value(value).endItem();
    }

    public DataStream item(String name, int value) {
        return startItem(name).value(value).endItem();
    }

    public DataStream item(String name, long value) {
        return startItem(name).value(value).endItem();
    }

    public DataStream item(String name, double value) {
        return startItem(name).value(value).endItem();
    }

    public DataStream item(String name, boolean value) {
        return startItem(name).value(value).endItem();
    }

    public abstract DataStream startMap();

    public abstract DataStream endMap();

    public abstract DataStream startEntry(String key);

    public abstract DataStream endEntry();

    public DataStream entry(String key, String value) {
        return startEntry(key).value(value).endEntry();
    }

    public DataStream entry(String key, Object value) {
        return startEntry(key).value(value).endEntry();
    }

    public DataStream entry(String key, int value) {
        return startEntry(key).value(value).endEntry();
    }

    public DataStream entry(String key, long value) {
        return startEntry(key).value(value).endEntry();
    }

    public DataStream entry(String key, double value) {
        return startEntry(key).value(value).endEntry();
    }

    public DataStream entry(String key, boolean value) {
        return startEntry(key).value(value).endEntry();
    }
    
    /* NOTE: the attrEntry methods that follow mirror the entry methods above.
     *       Both sets of methods are intended only for entries in maps.
     *       The attrEntry versions are specifically meant for the case where you're not sure
     *       your keys are valid XML element names. They will use a different XML serialization using
     *       an attribute for the key. */

    public DataStream attrEntry(String elementName, String attrName, String key, String value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    public DataStream attrEntry(String elementName, String attrName, String key, Object value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    public DataStream attrEntry(String elementName, String attrName, String key, int value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    public DataStream attrEntry(String elementName, String attrName, String key, long value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    public DataStream attrEntry(String elementName, String attrName, String key, double value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    public DataStream attrEntry(String elementName, String attrName, String key, boolean value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    public abstract DataStream startAttrEntry(String elementName, String attrName, String key);

    public abstract DataStream startAttrEntry(String elementName, String attrName, int key);

    public abstract DataStream endAttrEntry();

    public abstract DataStream contextList(List<Annotation> annotations, Set<Annotation> annotationsToList, List<String> values);

    public abstract DataStream value(String value);

    public DataStream value(Object value) {
        return value(value == null ? "" : value.toString());
    }

    public abstract DataStream value(long value);

    public abstract DataStream value(double value);

    public abstract DataStream value(boolean value);

    public DataStream plain(String value) {
        return print(value);
    }

    public static void main(String[] args) {
        PrintWriter out = new PrintWriter(System.out);

        DataStream test;
        test = new DataStreamXml(out, true);
        go(test, out);
        test = new DataStreamXml(out, false);
        go(test, out);
        test = new DataStreamJson(out, true, null);
        go(test, out);
//		test = new DataStreamJson(System.out, false, null);
//		go(test, out);
        test = new DataStreamJson(out, false, "myJsonCallback");
        go(test, out);

        out.flush();
    }

    private static void go(DataStream test, PrintWriter out) {
        test
                .startDocument("test")
                .entry("fun", true)
                .startEntry("cats")
                .startMap()
                .startAttrEntry("cat", "name", "Sylvie")
                .startAttrEntry("cat", "prefix:missing", "notdeclared")
                .startList()
                .item("place", "Voorschoten")
                .endList()
                .endEntry()
                .startAttrEntry("cat", "name", "Jelmer")
                .startList()
                .item("place", "Haarlem")
                .item("place", "Leiden")
                .item("place", "Haarlem")
                .endList()
                .endEntry()
                .entry("test", "bla")
                .attrEntry("test", "attr", "key", "value")
                .indent().startCompact().startAttrEntry("test", "attr", "key2").value("value2").endAttrEntry()
                .endCompact().newline()
                .endMap()
                .endEntry()
                .endDocument("test");
        out.println("");
    }

    public void setOmitEmptyProperties(boolean omitEmptyProperties) {
        this.omitEmptyProperties = omitEmptyProperties;
    }

}
