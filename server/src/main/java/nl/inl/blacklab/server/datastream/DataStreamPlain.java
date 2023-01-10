package nl.inl.blacklab.server.datastream;

import java.io.PrintWriter;
import java.util.Collection;
import java.util.List;

import nl.inl.blacklab.search.indexmetadata.Annotation;

/**
 * Disables all functions except for {@link DataStreamAbstract#plain(String)} Not ideal,
 * but required for certain outputs from requesthandlers. Since the main
 * requesthandler always adds some padding data, and we need to suppress this
 * for some data types.
 */
public class DataStreamPlain extends DataStreamAbstract {

    public DataStreamPlain(PrintWriter out, boolean prettyPrint) {
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
    public DataStreamAbstract endItem() {
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
    public DataStreamAbstract endEntry() {
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
    public DataStreamAbstract endAttrEntry() {
        return this;
    }

    @Override
    public DataStream contextList(List<Annotation> annotations, Collection<Annotation> annotationsToList, List<String> values) {
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
