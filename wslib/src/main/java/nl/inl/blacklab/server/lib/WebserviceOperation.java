package nl.inl.blacklab.server.lib;

public enum WebserviceOperation {
    SERVER_INFO("server-info"),
    CORPUS_INFO("corpus-info"),
    CORPUS_STATUS("corpus-status"),
    CORPUS_SHARING("corpus-sharing"),
    FIELD_INFO("field-info"),

    HITS("hits"),
    HITS_CSV("hits-csv"), // TODO: shouldn't be separate operations
    HITS_GROUPED("hits-grouped"),  // should -grouped be separate? (triggered by group/viewgroup params)
    DOCS("docs"),
    DOCS_CSV("docs-csv"), // TODO: shouldn't be separate operations
    DOCS_GROUPED("docs-grouped"),  // should -grouped be separate? (triggered by group/viewgroup params)

    DOC_CONTENTS("doc-contents"),
    DOC_INFO("doc-info"),
    DOC_SNIPPET("doc-snippet"),

    TERMFREQ("termfreq"),
    AUTOCOMPLETE("autocomplete"),

    LIST_INPUT_FORMATS("list-input-formats"),
    INPUT_FORMAT_INFO("input-format-info"),
    WRITE_INPUT_FORMAT("write-input-format"),
    INPUT_FORMAT_XSLT("input-format-xslt"),
    DELETE_INPUT_FORMAT("delete-input-format"),

    ADD_TO_CORPUS("add-to-corpus"),

    CACHE_INFO("cache-info"),
    CLEAR_CACHE("clear-cache"),
    CREATE_CORPUS("create-index"),
    DELETE_CORPUS("delete-corpus"),

    STATIC_RESPONSE("static-response"), // internal, used by BLS
    NONE("none");

    private final String name;

    public static WebserviceOperation fromName(String name, WebserviceOperation defVal) {
        for (WebserviceOperation t: values()) {
            if (t.name.equals(name))
                return t;
        }
        return defVal;
    }

    public static WebserviceOperation fromName(String name) {
        WebserviceOperation t = fromName(name, null);
        if (t == null)
            throw new IllegalArgumentException("Unknown WebserviceOperation: " + name);
        return t;
    }

    WebserviceOperation(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public boolean isDocsOperation() {
        return getName().startsWith("doc");
    }
}
