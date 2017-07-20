package nl.inl.blacklab.index.xpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration for an input format (either contents, or metadata, or a mix of both).
 */
public class ConfigInputFormat {

    /** This format's name */
    private String name;

    /** This format's display name (optional) */
    private String displayName = "";

    /** Pay attention to namespaces while parsing? */
    private boolean isNamespaceAware = true;

    /** XML namespace declarations */
    private Map<String, String> namespaces = new HashMap<>();

    /** How to find our documents */
    private String documentPath = "/";

    /** Should we store the document in the content store? (default: yes) */
    private boolean store = true;

    /** Before adding metadata fields to the document, this name mapping is applied. */
    private Map<String, String> indexFieldAs = new HashMap<>();

    /** Blocks of embedded metadata */
    private List<ConfigMetadataBlock> metadataBlocks = new ArrayList<>();

    /** Annotated fields (usually just "contents") */
    private List<ConfigAnnotatedField> annotatedFields = new ArrayList<>();

    /** Linked document(s), e.g. containing our metadata */
    private List<ConfigLinkedDocument> linkedDocuments = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public void setNamespaceAware(boolean isNamespaceAware) {
        this.isNamespaceAware = isNamespaceAware;
    }

    public void addNamespace(String name, String uri) {
        namespaces.put(name, uri);
    }

    public void setDocumentPath(String documentPath) {
        this.documentPath = documentPath;
    }

    public void addMetadataBlock(ConfigMetadataBlock b) {
        metadataBlocks.add(b);
    }

    public List<ConfigMetadataBlock> getMetadataBlocks() {
        return Collections.unmodifiableList(metadataBlocks);
    }

    public void addAnnotatedField(ConfigAnnotatedField f) {
        this.annotatedFields.add(f);
    }

    public void addLinkedDocument(ConfigLinkedDocument d) {
        linkedDocuments.add(d);
    }

    public boolean isNamespaceAware() {
        return isNamespaceAware;
    }

    public Map<String, String> getNamespaces() {
        return namespaces;
    }

    public String getDocumentPath() {
        return documentPath;
    }

    public List<ConfigAnnotatedField> getAnnotatedFields() {
        return Collections.unmodifiableList(annotatedFields);
    }

    public List<ConfigLinkedDocument> getLinkedDocuments() {
        return linkedDocuments;
    }

    public Map<String, String> getIndexFieldAs() {
        return indexFieldAs;
    }

    public void addIndexFieldAs(String from, String to) {
        indexFieldAs.put(from, to);
    }

    public boolean isStore() {
        return store;
    }

    public void setStore(boolean store) {
        this.store = store;
    }

}