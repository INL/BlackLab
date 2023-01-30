package nl.inl.blacklab.webservice;

public enum WebserviceOperation {
    SERVER_INFO("server-info", ""),
    CORPUS_INFO("corpus-info", ""),
    CORPUS_STATUS("corpus-status", "status"),
    CORPUS_SHARING("corpus-sharing", "sharing"),
    FIELD_INFO("field-info", "fields"),

    HITS("hits", "hits"),
    HITS_CSV("hits-csv", "hits"), // TODO: shouldn't be separate operations
    HITS_GROUPED("hits-grouped", "hits"),  // should -grouped be separate? (triggered by group/viewgroup params)
    DOCS("docs", "docs"),
    DOCS_CSV("docs-csv", "docs"), // TODO: shouldn't be separate operations
    DOCS_GROUPED("docs-grouped", "docs"),  // should -grouped be separate? (triggered by group/viewgroup params)

    DOC_CONTENTS("doc-contents", "docs"),
    DOC_INFO("doc-info", "docs"),
    DOC_SNIPPET("doc-snippet", "docs"),

    TERMFREQ("termfreq", "termfreq"),
    AUTOCOMPLETE("autocomplete", "autocomplete"),

    LIST_INPUT_FORMATS("list-input-formats", "input-formats"),
    INPUT_FORMAT_INFO("input-format-info", "input-formats"),
    INPUT_FORMAT_XSLT("input-format-xslt", "input-formats"),
    WRITE_INPUT_FORMAT("write-input-format", "input-formats"),
    DELETE_INPUT_FORMAT("delete-input-format", "input-formats"),

    ADD_TO_CORPUS("add-to-corpus", ""),

    CACHE_INFO("cache-info", "cache-info"),
    CACHE_CLEAR("cache-clear", "cache-clear"),

    CREATE_CORPUS("create-corpus", ""),
    DELETE_CORPUS("delete-corpus", ""),

    STATIC_RESPONSE("static-response", ""), // internal, used by BLS
    NONE("none", "");

    private final String name;

    private final String blsPath;

    public static WebserviceOperation fromValue(String name, WebserviceOperation defVal) {
        for (WebserviceOperation t: values()) {
            if (t.name.equals(name))
                return t;
        }
        return defVal;
    }

    public static WebserviceOperation fromValue(String name) {
        WebserviceOperation t = fromValue(name, null);
        if (t == null)
            throw new IllegalArgumentException("Unknown WebserviceOperation: " + name);
        return t;
    }

    WebserviceOperation(String name, String blsPath) {
        this.name = name;
        this.blsPath = blsPath;
    }

    public String value() {
        return name;
    }

    public boolean isDocsOperation() {
        return value().startsWith("doc");
    }

    public String blsPath() {
        return blsPath;
    }
}
