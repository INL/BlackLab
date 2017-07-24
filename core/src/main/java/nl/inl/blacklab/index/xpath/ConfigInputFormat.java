package nl.inl.blacklab.index.xpath;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nl.inl.blacklab.index.DocumentFormats;

/**
 * Configuration for an input format (either contents, or metadata, or a mix of both).
 */
public class ConfigInputFormat {

    /** This format's name */
    private String name;

    /** This format's display name (optional) */
    private String displayName = "";

    /** This format's description (optional) */
    private String description = "";

    /** Pay attention to namespaces while parsing? */
    private boolean isNamespaceAware = true;

    /** XML namespace declarations */
    private Map<String, String> namespaces = new LinkedHashMap<>();

    /** How to find our documents */
    private String documentPath = "/";

    /** Should we store the document in the content store? (default: yes) */
    private boolean store = true;

    /** Before adding metadata fields to the document, this name mapping is applied. */
    private Map<String, String> indexFieldAs = new LinkedHashMap<>();

    /** Special field roles, such as pidField, titleField, etc. */
    private Map<String, String> specialFields = new LinkedHashMap<>();

    /** How to group metadata fields */
    private Map<String, ConfigMetadataFieldGroup> metadataFieldGroups = new LinkedHashMap<>();

    /** What default analyzer to use if not overridden */
    private String metadataDefaultAnalyzer = "default";

    /** Blocks of embedded metadata */
    private List<ConfigMetadataBlock> metadataBlocks = new ArrayList<>();

    /** Annotated fields (usually just "contents") */
    private Map<String, ConfigAnnotatedField> annotatedFields = new LinkedHashMap<>();

    /** Linked document(s), e.g. containing our metadata */
    private Map<String, ConfigLinkedDocument> linkedDocuments = new LinkedHashMap<>();

    /**
     * Copy everything except name, displayName and description from the specified format.
     * @param formatName format to copy from
     */
    public void setBaseFormat(String formatName) {
        ConfigInputFormat baseFormat = DocumentFormats.getConfig(formatName);
        isNamespaceAware = baseFormat.isNamespaceAware();
        namespaces.putAll(baseFormat.getNamespaces());
        documentPath = baseFormat.getDocumentPath();
        store = baseFormat.isStore();
        indexFieldAs.putAll(baseFormat.getIndexFieldAs());
        specialFields.putAll(baseFormat.getSpecialFields());
        for (ConfigMetadataFieldGroup g: baseFormat.getMetadataFieldGroups().values()) {
            addMetadataFieldGroup(g);
        }
        metadataDefaultAnalyzer = baseFormat.getMetadataDefaultAnalyzer();
        for (ConfigMetadataBlock b: baseFormat.getMetadataBlocks()) {
            addMetadataBlock(b.copy());
        }
        for (ConfigAnnotatedField f: baseFormat.getAnnotatedFields().values()) {
            addAnnotatedField(f.copy());
        }
        linkedDocuments.putAll(baseFormat.getLinkedDocuments());
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    void addMetadataBlock(ConfigMetadataBlock b) {
        if (b.getAnalyzer().isEmpty())
            b.setAnalyzer(metadataDefaultAnalyzer);
        metadataBlocks.add(b);
    }

    public ConfigMetadataBlock createMetadataBlock() {
        ConfigMetadataBlock b = new ConfigMetadataBlock();
        b.setAnalyzer(metadataDefaultAnalyzer);
        metadataBlocks.add(b);
        return b;
    }

    public List<ConfigMetadataBlock> getMetadataBlocks() {
        return Collections.unmodifiableList(metadataBlocks);
    }

    public void addAnnotatedField(ConfigAnnotatedField f) {
        this.annotatedFields.put(f.getFieldName(), f);
    }

    public void addLinkedDocument(ConfigLinkedDocument d) {
        linkedDocuments.put(d.getName(), d);
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

    public Map<String, ConfigAnnotatedField> getAnnotatedFields() {
        return Collections.unmodifiableMap(annotatedFields);
    }

    public ConfigAnnotatedField getAnnotatedField(String name) {
        return getAnnotatedField(name, false);
    }

    public ConfigAnnotatedField getOrCreateAnnotatedField(String name) {
        return getAnnotatedField(name, true);
    }

    private ConfigAnnotatedField getAnnotatedField(String name, boolean createIfNotFound) {
        ConfigAnnotatedField f = annotatedFields.get(name);
        if (f == null && createIfNotFound) {
            f = new ConfigAnnotatedField(name);
            annotatedFields.put(name, f);
        }
        return f;
    }

    public Map<String, ConfigLinkedDocument> getLinkedDocuments() {
        return Collections.unmodifiableMap(linkedDocuments);
    }

    public ConfigLinkedDocument getLinkedDocument(String name) {
        return getLinkedDocument(name, false);
    }

    public ConfigLinkedDocument getOrCreateLinkedDocument(String string) {
        return getLinkedDocument(name, true);
    }

    private ConfigLinkedDocument getLinkedDocument(String name, boolean createIfNotFound) {
        ConfigLinkedDocument ld = linkedDocuments.get(name);
        if (ld == null && createIfNotFound) {
            ld = new ConfigLinkedDocument(name);
            linkedDocuments.put(name, ld);
        }
        return ld;
    }

    public Map<String, String> getIndexFieldAs() {
        return Collections.unmodifiableMap(indexFieldAs);
    }

    public void addIndexFieldAs(String from, String to) {
        indexFieldAs.put(from, to);
    }

    public Map<String, String> getSpecialFields() {
        return Collections.unmodifiableMap(specialFields);
    }

    public void addSpecialField(String type, String fieldName) {
        specialFields.put(type, fieldName);
    }

    public boolean isStore() {
        return store;
    }

    public void setStore(boolean store) {
        this.store = store;
    }

    public String getMetadataDefaultAnalyzer() {
        return metadataDefaultAnalyzer;
    }

    public void setMetadataAnalyzer(String metadataDefaultAnalyzer) {
        this.metadataDefaultAnalyzer = metadataDefaultAnalyzer;
    }

    public Map<String, ConfigMetadataFieldGroup> getMetadataFieldGroups() {
        return metadataFieldGroups;
    }

    void addMetadataFieldGroup(ConfigMetadataFieldGroup g) {
        metadataFieldGroups.put(g.getName(), g);
    }

    public ConfigMetadataFieldGroup getOrCreateMetadataFieldGroup(String name) {
        ConfigMetadataFieldGroup g = metadataFieldGroups.get(name);
        if (g == null) {
            g = new ConfigMetadataFieldGroup(name);
            metadataFieldGroups.put(name, g);
        }
        return g;
    }

}