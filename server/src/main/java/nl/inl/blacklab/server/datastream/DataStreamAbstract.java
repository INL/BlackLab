package nl.inl.blacklab.server.datastream;

import java.io.PrintWriter;

import nl.inl.blacklab.server.lib.results.ApiVersion;

/**
 * Class to stream out XML or JSON data.
 *
 * This is faster than building a full object tree first. Intended to replace
 * the DataObject classes.
 */
public abstract class DataStreamAbstract implements DataStream {
    
    public static DataStream create(DataFormat format, PrintWriter out, boolean prettyPrint, ApiVersion api) {
        if (format == DataFormat.JSON)
            return new DataStreamJson(out, prettyPrint);
        if (format == DataFormat.CSV)
            return new DataStreamCsv(out, prettyPrint);
        return new DataStreamXml(out, prettyPrint, api);
    }

    protected final PrintWriter out;

    private int indent = 0;

    private int compactLevel = 0;

    private boolean prettyPrint;

    private final boolean prettyPrintPref;

    public DataStreamAbstract(PrintWriter out, boolean prettyPrint) {
        this.out = out;
        this.prettyPrintPref = this.prettyPrint = prettyPrint;
    }

    public DataStreamAbstract print(String str) {
        out.print(str);
        return this;
    }

    public DataStreamAbstract print(long value) {
        out.print(value);
        return this;
    }

    public DataStreamAbstract print(double value) {
        out.print(value);
        return this;
    }

    public DataStreamAbstract print(boolean value) {
        out.print(value ? "true" : "false");
        return this;
    }

    DataStreamAbstract pretty(String str) {
        if (prettyPrint)
            print(str);
        return this;
    }

    DataStreamAbstract upindent() {
        indent++;
        return this;
    }

    DataStreamAbstract downindent() {
        indent--;
        return this;
    }

    DataStreamAbstract indent() {
        if (prettyPrint) {
            for (int i = 0; i < indent; i++) {
                print("  ");
            }
        }
        return this;
    }

    DataStreamAbstract newlineIndent() {
        return newline().indent();
    }

    @Override
    public DataStreamAbstract newline() {
        return pretty("\n");
    }

    public DataStreamAbstract space() {
        return pretty(" ");
    }

    @Override
    public DataStreamAbstract endCompact() {
        compactLevel--;
        assert compactLevel >= 0;
        if (compactLevel == 0)
            prettyPrint = prettyPrintPref;
        return this;
    }

    @Override
    public DataStream startCompact() {
        assert compactLevel >= 0;
        prettyPrint = false;
        compactLevel++;
        return this;
    }

    @Override
    public void outputProlog() {
        // subclasses may override
    }

    /* NOTE: the attrEntry methods that follow mirror the entry methods above.
     *       Both sets of methods are intended only for entries in maps.
     *       The attrEntry versions are specifically meant for the case where you're not sure
     *       your keys are valid XML element names. They will use a different XML serialization using
     *       an attribute for the key. */

    @Override
    public DataStream plain(String value) {
        return print(value);
    }

    /**
     * Output an XML fragment, either as a string
     * value or as part of the XML structure.
     *
     * DataStreamXML overrides this methods to output the fragment
     * unquoted and -escaped. Used with usecontent=orig.
     *
     * @param fragment
     * @return data stream
     */
    @Override
    public DataStream xmlFragment(String fragment) {
        return value(fragment);
    }

}
