package nl.inl.blacklab.server.datastream;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.text.StringEscapeUtils;

import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Class to stream out XML data.
 *
 * This is faster than building a full object tree first. Intended to replace
 * the DataObject classes.
 */
public class DataStreamXml extends DataStreamAbstract {

    final List<String> tagStack = new ArrayList<>();

    /** Root el document was started with (or null if none) */
    private String rootEl;

    /** Should contextList omit empty annotations if possible? */
    protected boolean omitEmptyAnnotations = false;

    @Override
    public void setOmitEmptyAnnotations(boolean omitEmptyAnnotations) {
        this.omitEmptyAnnotations = omitEmptyAnnotations;
    }

    public DataStreamXml(PrintWriter out, boolean prettyPrint) {
        super(out, prettyPrint);
    }

    public DataStream startOpenEl(String name) {
        tagStack.add(name);
        return indent().print("<").print(name);
    }

    private DataStream attr(String key, String value) {
        return print(" ").print(key).print("=\"").print(StringEscapeUtils.escapeXml10(value)).print("\"");
    }

    public DataStream endOpenEl() {
        return print(">").upindent().newline();
    }

    private DataStreamXml openEl(String name) {
        startOpenEl(name);
        endOpenEl();
        return this;
    }

    public DataStreamXml closeEl() {
        String name = tagStack.remove(tagStack.size() - 1);
        downindent().indent().print("</").print(name).print(">").newline();
        return this;
    }

    @Override
    public void outputProlog() {
        print(XML_PROLOG).newline();
    }

    @Override
    public DataStream startDocument(String rootEl) {
        this.rootEl = rootEl;
        if (rootEl == null)
            return this;
        outputProlog();
        startOpenEl(rootEl);
        return endOpenEl();
    }

    @Override
    public DataStream endDocument() {
        if (rootEl == null)
            return this;
        startCompact();
        return closeEl().endCompact();
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
    public DataStream item(String name, String value) {
        return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
    }

    @Override
    public DataStream item(String name, Object value) {
        return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
    }

    @Override
    public DataStream item(String name, int value) {
        return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
    }

    @Override
    public DataStream item(String name, long value) {
        return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
    }

    @Override
    public DataStream item(String name, double value) {
        return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
    }

    @Override
    public DataStream item(String name, boolean value) {
        return indent().startCompact().startItem(name).value(value).endItem().endCompact().newline();
    }

    @Override
    public DataStream startItem(String name) {
        return openEl(name);
    }

    @Override
    public DataStreamAbstract endItem() {
        return closeEl();
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
    public DataStream entry(String key, String value) {
        return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
    }

    @Override
    public DataStream entry(String key, Object value) {
        return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
    }

    @Override
    public DataStream entry(String key, int value) {
        return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
    }

    @Override
    public DataStream entry(String key, long value) {
        return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
    }

    @Override
    public DataStream entry(String key, double value) {
        return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
    }

    @Override
    public DataStream entry(String key, boolean value) {
        return indent().startCompact().startEntry(key).value(value).endEntry().endCompact().newline();
    }

    @Override
    public DataStream startEntry(String key) {
        return openEl(key);
    }

    @Override
    public DataStreamAbstract endEntry() {
        return closeEl();
    }

    @Override
    public DataStream attrEntry(String elementName, String attrName, String key, Object value) {
        return indent().startCompact().startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
                .endCompact().newline();
    }

    @Override
    public DataStream attrEntry(String elementName, String attrName, String key, long value) {
        return indent().startCompact().startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
                .endCompact().newline();
    }

    @Override
    public DataStream attrEntry(String elementName, String attrName, String key, String value) {
        return indent().startCompact().startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
                .endCompact().newline();
    }

    @Override
    public DataStream attrEntry(String elementName, String attrName, String key, int value) {
        return indent().startCompact().startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
                .endCompact().newline();
    }

    @Override
    public DataStream attrEntry(String elementName, String attrName, String key, double value) {
        return indent().startCompact().startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
                .endCompact().newline();
    }

    @Override
    public DataStream attrEntry(String elementName, String attrName, String key, boolean value) {
        return indent().startCompact().startAttrEntry(elementName, attrName, key).value(value).endAttrEntry()
                .endCompact().newline();
    }

    @Override
    public DataStream startAttrEntry(String elementName, String attrName, String key) {
        startOpenEl(elementName);
        attr(attrName, key);
        return endOpenEl();
    }

    @Override
    public DataStream startAttrEntry(String elementName, String attrName, int key) {
        return startEntry(Integer.toString(key));
    }

    @Override
    public DataStreamAbstract endAttrEntry() {
        return closeEl();
    }

    @Override
    public DataStream startElEntry(String key) {
        return openEl("entry").openEl("key").value(key).closeEl().openEl("value");
    }

    @Override
    public DataStream endElEntry() {
        return closeEl().closeEl(); // close value and entry elements
    }

    @Override
    public DataStream contextList(List<Annotation> annotations, Collection<Annotation> annotationsToList, List<String> values) {
        upindent();
        int valuesPerWord = annotations.size();
        int numberOfWords = values.size() / valuesPerWord;
        for (int i = 0; i < numberOfWords; i++) {
            int vIndex = i * valuesPerWord;
            int j = 0;
            indent();
            if (annotationsToList.contains(annotations.get(0))) { // punctuation
                print(StringEscapeUtils.escapeXml10(values.get(vIndex)));
            }
            print("<w");
            for (int k = 1; k < annotations.size() - 1; k++) {
                Annotation annotation = annotations.get(k);
                String value = values.get(vIndex + 1 + j);
                if (annotationsToList.contains(annotation) && (!omitEmptyAnnotations || !value.isEmpty()))
                    print(" ").print(annotation.name()).print("=\"").print(StringEscapeUtils.escapeXml10(value)).print("\"");
                j++;
            }
            print(">");
            print(StringEscapeUtils.escapeXml10(values.get(vIndex + 1 + j)));
            print("</w>");
            newline();
        }
        return downindent();
    }

    @Override
    public DataStreamXml value(String value) {
        indent();
        if (value == null)
            print("(null)");
        else
            print(StringEscapeUtils.escapeXml10(value));
        newline();
        return this;
    }

    @Override
    public DataStream value(long value) {
        return indent().print(value).newline();
    }

    @Override
    public DataStream value(double value) {
        return indent().print(value).newline();
    }

    @Override
    public DataStream value(boolean value) {
        return indent().print(value).newline();
    }

    /**
     * Output an XML fragment, either as a string
     * value or as part of the XML structure.
     *
     * @param fragment
     * @return data stream
     */
    public DataStream xmlFragment(String fragment) {
        // Because we're outputting XML, we output the fragment plain (unquoted or -escaped)
        return plain(fragment);
    }

}
