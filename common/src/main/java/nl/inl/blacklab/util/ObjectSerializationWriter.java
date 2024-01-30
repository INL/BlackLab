package nl.inl.blacklab.util;

@FunctionalInterface
public interface ObjectSerializationWriter {
    /**
     * @param type text pattern node type, e.g. "term", "sequence", "repeat", ...
     * @param args Sorted list of alternating argument names and values, e.g. key, value, key, value, ...
     */
    void write(String type, Object... args);
}
