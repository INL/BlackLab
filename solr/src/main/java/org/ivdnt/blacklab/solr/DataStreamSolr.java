package org.ivdnt.blacklab.solr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.response.SolrQueryResponse;

import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.server.datastream.DataStream;

public class DataStreamSolr implements DataStream {

    private final SolrQueryResponse rsp;

    /** Parent objects to the current object we're writing. */
    private final List<Object> parents = new ArrayList<>();

    /** Current NamedList or List we're writing. */
    private Object currentObject = null;

    /** The key for which we're about to write a value (if currentObject is a NamedList) */
    private String currentKey = null;

    public DataStreamSolr(SolrQueryResponse rsp) {
        this.rsp = rsp;
    }

    @Override
    public DataStream endCompact() {
        return this;
    }

    @Override
    public DataStream startCompact() {
        return null;
    }

    @Override
    public void outputProlog() {
        // NOP
    }

    @Override
    public DataStream startDocument(String rootEl) {
        startStructure(new NamedList());
        return this;
    }

    @Override
    public DataStream endDocument() {
        if (currentObject != null) {
            Object v = currentObject;
            endStructure(NamedList.class);
            NamedList<Object> nl = (NamedList<Object>)v;
            nl.forEach((name, value) -> {
                rsp.add(name, value);
            });
        }
        return this;
    }

    @Override
    public DataStream startList() {
        startStructure(new ArrayList<>());
        return this;
    }

    /** Start a list or object as a substructure of the current structure
     *  (i.e. a list item or an object entry value)
     * @param l structure we're starting (must be a List or a NamedList)
     */
    private void startStructure(Object l) {
        if (currentObject != null) {
            addValueToCurrentStructure(l);
            parents.add(currentObject);
        }
        currentObject = l;
    }

    private void addValueToCurrentStructure(Object l) {
        if (currentObject instanceof List) {
            // List item
            ((List) currentObject).add(l);
        } else if (currentObject instanceof NamedList) {
            // Object entry (not top-level)
            if (currentKey == null) {
                throw new IllegalStateException("No key set when adding value: " + l);
            } else {
                ((NamedList) currentObject).add(currentKey, l);
                currentKey = null;
            }
        } else if (currentObject == null && parents.isEmpty()) {
            // Top-level entry
            if (currentKey == null) {
                throw new IllegalStateException("No key set when adding value: " + l);
            } else {
                rsp.add(currentKey, l);
                currentKey = null;
            }
        } else if (currentObject == null) {
            throw new IllegalStateException("No current object");
        } else {
            throw new IllegalStateException(
                    "Current object not a List or NamedList: " + currentObject.getClass().getName());
        }
    }

    private void ensureList() {
        if (!(currentObject instanceof List))
            throw new IllegalStateException("Current object is not a list");
    }

    private void ensureNamedList() {
        if (!(currentObject instanceof NamedList))
            throw new IllegalStateException("Current object is not a named list");
    }

    private void ensureType(Class<?> clz) {
        if (clz == List.class)
            ensureList();
        else if (clz == NamedList.class)
            ensureNamedList();
        else
            throw new IllegalArgumentException("Structure type can only be List or NamedList, got " + clz.getName());
    }

    private void endStructure(Class<?> clz) {
        ensureType(clz);
        if (currentObject == null)
            throw new IllegalStateException("No structure opened");
        if (parents.isEmpty())
            currentObject = null;
        else
            currentObject = parents.remove(parents.size() - 1);
    }

    @Override
    public DataStream endList() {
        endStructure(List.class);
        return this;
    }

    @Override
    public DataStream startItem(String name) {
        ensureList();
        return this;
    }

    @Override
    public DataStream endItem() {
        ensureList();
        return this;
    }

    @Override
    public DataStream startMap() {
        startStructure(new NamedList());
        return this;
    }

    @Override
    public DataStream endMap() {
        endStructure(NamedList.class);
        return this;
    }

    @Override
    public DataStream startEntry(String key) {
        currentKey = key;
        return this;
    }

    @Override
    public DataStream endEntry() {
        return this;
    }

    @Override
    public DataStream startAttrEntry(String elementName, String attrName, String key) {
        return startEntry(key);
    }

    @Override
    public DataStream startAttrEntry(String elementName, String attrName, int key) {
        return startAttrEntry(elementName, attrName, key + "");
    }

    @Override
    public DataStream endAttrEntry() {
        return endEntry();
    }

    @Override
    public DataStream contextList(List<Annotation> annotations, Collection<Annotation> annotationsToList,
            List<String> values) {
        //@@@ TODO
        return this;
    }

    @Override
    public DataStream value(String value) {
        addValueToCurrentStructure(value);
        return this;
    }

    @Override
    public DataStream value(long value) {
        addValueToCurrentStructure(value);
        return this;
    }

    @Override
    public DataStream value(double value) {
        addValueToCurrentStructure(value);
        return this;
    }

    @Override
    public DataStream value(boolean value) {
        addValueToCurrentStructure(value);
        return this;
    }

    @Override
    public DataStream plain(String value) {
        //@@@@ TODO
        return this;
    }

    @Override
    public void setOmitEmptyAnnotations(boolean omitEmptyAnnotations) {
        // NOP
    }

    @Override
    public DataStream xmlFragment(String fragment) {
        value(fragment); //@@@ CHECK
        return this;
    }

    @Override
    public DataStream space() {
        return this;
    }

    @Override
    public DataStream newline() {
        return this;
    }
}
