package nl.inl.blacklab.webservice;

import java.util.Optional;

/**
 * All of the operations a BlackLab webservice supports.
 *
 * Also records the BlackLab Server URL path and HTTP method.
 * The path doesn't apply to Solr, because Solr always uses the same path
 * and gets all info via parameters.
 */
public enum WebserviceOperation {
    SERVER_INFO("server-info", ""),
    CORPUS_INFO("corpus-info", ""),
    CORPUS_STATUS("corpus-status", "status"),
    CORPUS_SHARING("corpus-sharing", "sharing"),
    FIELD_INFO("field-info", "fields"),

    HITS("hits", "hits"),
    HITS_CSV("hits-csv", "hits"), // TODO: shouldn't be separate operations
    HITS_GROUPED("hits-grouped", "hits"),  // should -grouped be separate? (triggered by group/viewgroup params)
    PARSE_PATTERN("parse-pattern", "parse-pattern"),

    RELATIONS("relations", "relations"),

    DOCS("docs", "docs"),
    DOCS_CSV("docs-csv", "docs"), // TODO: shouldn't be separate operations
    DOCS_GROUPED("docs-grouped", "docs"),  // should -grouped be separate? (triggered by group/viewgroup params)

    DOC_CONTENTS("doc-contents", "docs"),
    DOC_INFO("doc-info", "docs"),
    DOC_SNIPPET("doc-snippet", "docs"),

    TERM_FREQUENCIES("termfreq", "termfreq"),
    AUTOCOMPLETE("autocomplete", "autocomplete"),

    LIST_INPUT_FORMATS("list-input-formats", "input-formats"),
    INPUT_FORMAT_INFO("input-format-info", "input-formats"),
    INPUT_FORMAT_XSLT("input-format-xslt", "input-formats"),
    WRITE_INPUT_FORMAT("write-input-format", "POST", "input-formats"),
    DELETE_INPUT_FORMAT("delete-input-format", "DELETE", "input-formats"),

    ADD_TO_CORPUS("add-to-corpus", "POST", ""),

    CACHE_INFO("cache-info", "cache-info"),
    CACHE_CLEAR("cache-clear", "POST", "cache-clear"),

    CREATE_CORPUS("create-corpus", "POST", ""),
    DELETE_CORPUS("delete-corpus", "DELETE", ""),

    STATIC_RESPONSE("static-response", ""), // internal, used by BLS
    NONE("none", "");

    private final String value;

    private final String blsPath;

    private final String httpMethod;

    public static Optional<WebserviceOperation> fromValue(String name) {
        for (WebserviceOperation t: values()) {
            if (t.value.equals(name))
                return Optional.of(t);
        }
        return Optional.empty();
    }

    WebserviceOperation(String value, String blsPath) {
        this(value, "GET", blsPath);
    }

    WebserviceOperation(String value, String httpMethod, String blsPath) {
        this.value = value;
        this.blsPath = blsPath;
        this.httpMethod = httpMethod;
    }

    public String value() { return value; }

    @Override
    public String toString() { return value(); }

    public boolean isDocsOperation() {
        return value().startsWith("doc");
    }

    public String path() {
        return blsPath;
    }

    public String getHttpMethod() {
        return httpMethod;
    }
}
