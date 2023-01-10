package nl.inl.blacklab.server.datastream;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.search.indexmetadata.Annotation;
import nl.inl.blacklab.server.util.WebserviceUtil;

public interface DataStream {
    /**
     * Construct a simple status response object.
     *
     * Status response may indicate success, or e.g. that the server is carrying out
     * the request and will have results later.
     *
     * @param code (string) status code
     * @param msg the message
     */
    default void statusObject(String code, String msg) {
        startMap()
                .startEntry("status")
                .startMap()
                .entry("code", code)
                .entry("message", msg);
        endMap()
                .endEntry()
                .endMap();
    }

    /**
     * Construct a simple error response object.
     *
     * @param code (string) error code
     * @param msg the error message
     * @param e if specified, include stack trace
     */
    default void error(String code, String msg, Throwable e) {
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
    default void error(String code, String msg) {
        error(code, msg, null);
    }

    default void internalError(Exception e, boolean debugMode, String code) {
        error("INTERNAL_ERROR", WebserviceUtil.internalErrorMessage(e, debugMode, code), debugMode ? e : null);
    }

    default void internalError(String message, boolean debugMode, String code) {
        error("INTERNAL_ERROR", WebserviceUtil.internalErrorMessage(message, debugMode, code));
    }

    default void internalError(String code) {
        error("INTERNAL_ERROR", WebserviceUtil.internalErrorMessage(code));
    }

    DataStream endCompact();

    DataStream startCompact();

    void outputProlog();

    DataStream startDocument(String rootEl);

    DataStream endDocument();

    DataStream startList();

    DataStream endList();

    DataStream startItem(String name);

    DataStream endItem();

    default DataStream item(String name, String value) {
        return startItem(name).value(value).endItem();
    }

    default DataStream item(String name, Object value) {
        return startItem(name).value(value).endItem();
    }

    default DataStream item(String name, int value) {
        return startItem(name).value(value).endItem();
    }

    default DataStream item(String name, long value) {
        return startItem(name).value(value).endItem();
    }

    default DataStream item(String name, double value) {
        return startItem(name).value(value).endItem();
    }

    default DataStream item(String name, boolean value) {
        return startItem(name).value(value).endItem();
    }

    DataStream startMap();

    DataStream endMap();

    DataStream startEntry(String key);

    DataStream endEntry();

    default DataStream entry(String key, String value) {
        return startEntry(key).value(value).endEntry();
    }

    default DataStream entry(String key, Object value) {
        return startEntry(key).value(value).endEntry();
    }

    default DataStream entry(String key, int value) {
        return startEntry(key).value(value).endEntry();
    }

    default DataStream entry(String key, long value) {
        return startEntry(key).value(value).endEntry();
    }

    default DataStream entry(String key, double value) {
        return startEntry(key).value(value).endEntry();
    }

    default DataStream entry(String key, boolean value) {
        return startEntry(key).value(value).endEntry();
    }

    default DataStream attrEntry(String elementName, String attrName, String key, String value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    default DataStream attrEntry(String elementName, String attrName, String key, Object value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    default DataStream attrEntry(String elementName, String attrName, String key, int value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    default DataStream attrEntry(String elementName, String attrName, String key, long value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    default DataStream attrEntry(String elementName, String attrName, String key, double value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    default DataStream attrEntry(String elementName, String attrName, String key, boolean value) {
        return startAttrEntry(elementName, attrName, key).value(value).endAttrEntry();
    }

    DataStream startAttrEntry(String elementName, String attrName, String key);

    DataStream startAttrEntry(String elementName, String attrName, int key);

    DataStream endAttrEntry();

    DataStream contextList(List<Annotation> annotations, Collection<Annotation> annotationsToList, List<String> values);

    DataStream value(String value);

    DataStream value(long value);

    DataStream value(double value);

    DataStream value(boolean value);

    /**
     * Output a map.
     *
     * May contain nested structures (Map, List) and/or values.
     *
     * @param value map to output
     * @param <S> entry key type
     * @param <T> entry value type
     * @return this data stream
     */
    default <S, T> DataStream value(Map<S, T> value) {
        startMap();
        if (value != null) {
            for (Map.Entry<S, T> entry : value.entrySet()) {
                startEntry(entry.getKey().toString()).value(entry.getValue()).endEntry();
            }
        }
        endMap();
        return this;
    }

    /**
     * Output a list.
     *
     * May contain nested structures (Map, List) and/or values.
     *
     * Uses "item" for the list item name (in XML mode).
     *
     * @param value list to output
     * @param <T> list item type
     * @return this data stream
     */
    default <T> DataStream value(List<T> value) {
        startList();
        if (value != null) {
            for (T item : value) {
                startItem("item").value(item).endItem();
            }
        }
        endList();
        return this;
    }

    /**
     * Output a value that may be a nested structure (Map or List) or simple value.
     *
     * @param value value to output
     * @return this data stream
     */
    default DataStream value(Object value) {
        if (value instanceof Map) {
            return value((Map)value);
        } else if (value instanceof List) {
            return value((List)value);
        } else if (value instanceof String) {
            return value((String)value);
        } else if (value instanceof Integer || value instanceof Long) {
            return value(((Number) value).longValue());
        } else if (value instanceof Double || value instanceof Float) {
            return value(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            return value((boolean)value);
        } else {
            return value(value == null ? "" : value.toString());
        }
    }

    DataStream plain(String value);

    void setOmitEmptyAnnotations(boolean omitEmptyAnnotations);

    DataStream xmlFragment(String fragment);

    default <T> void list(String itemName, T[] items) {
        startList();
        for (T i: items) {
            item(itemName, i);
        }
        endList();
    }

    default <T> void list(String itemName, Iterable<T> items) {
        startList();
        for (T i: items) {
            item(itemName, i);
        }
        endList();
    }

    DataStream newline();

    DataStream space();
}
