package nl.inl.blacklab.index.xpath;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

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

    /** This format's type indicator (optional, not used by BlackLab) */
    private String type = "";

    /** May end user fetch contents of whole documents? [false] */
    private boolean contentViewable = false;

    /** XML namespace declarations */
    Map<String, String> namespaces = new LinkedHashMap<>();

    /** How to find our documents */
    private String documentPath = "/";

    /** Should we store the document in the content store? (default: yes) */
    private boolean store = true;

    /** Before adding metadata fields to the document, this name mapping is applied. */
    Map<String, String> indexFieldAs = new LinkedHashMap<>();

    /** Special field roles, such as pidField, titleField, etc. */
    Map<String, String> specialFields = new LinkedHashMap<>();

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

    public ConfigInputFormat() {
        // NOP
    }

    public ConfigInputFormat(File file) throws IOException {
        InputFormatReader.read(file, this);
    }

    public ConfigInputFormat(Reader reader, boolean isJson) throws IOException {
        InputFormatReader.read(reader, isJson, this);
    }

    /**
     * Copy everything except name, displayName and description from the specified format.
     * @param formatName format to copy from
     */
    public void setBaseFormat(String formatName) {
        ConfigInputFormat baseFormat = DocumentFormats.getConfig(formatName);
        if (baseFormat == null)
            throw new InputFormatConfigException("Base format " + formatName + " not found for format " + name);
        type = baseFormat.getType();
        contentViewable = baseFormat.isContentViewable();
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

    /**
     * Validate this configuration.
     */
    public void validate() {
        String t = "input format";
        req(name, t, "name");
        req(documentPath, t, "documentPath");
        for (ConfigMetadataBlock b: metadataBlocks)
            b.validate();
        for (ConfigAnnotatedField af: annotatedFields.values())
            af.validate();
        for (ConfigLinkedDocument ld: linkedDocuments.values())
            ld.validate();
    }

    static void req(String value, String type, String name) {
        if (value == null || value.isEmpty())
            throw new InputFormatConfigException(StringUtils.capitalize(type) + " must have a " + name);
    }

    static void req(boolean test, String type, String mustMsg) {
        if (!test)
            throw new InputFormatConfigException(StringUtils.capitalize(type) + " must " + mustMsg);
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

    public void addNamespace(String name, String uri) {
        namespaces.put(name, uri);
    }

    public void setDocumentPath(String documentPath) {
        this.documentPath = documentPath;
    }

    void addMetadataBlock(ConfigMetadataBlock b) {
        if (b.getAnalyzer().isEmpty())
            b.setDefaultAnalyzer(metadataDefaultAnalyzer);
        metadataBlocks.add(b);
    }

    public ConfigMetadataBlock createMetadataBlock() {
        ConfigMetadataBlock b = new ConfigMetadataBlock();
        b.setDefaultAnalyzer(metadataDefaultAnalyzer);
        metadataBlocks.add(b);
        return b;
    }

    public List<ConfigMetadataBlock> getMetadataBlocks() {
        return Collections.unmodifiableList(metadataBlocks);
    }

    public void addAnnotatedField(ConfigAnnotatedField f) {
        this.annotatedFields.put(f.getName(), f);
    }

    public void addLinkedDocument(ConfigLinkedDocument d) {
        linkedDocuments.put(d.getName(), d);
    }

    public boolean isNamespaceAware() {
        return namespaces.size() > 0;
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

    public ConfigLinkedDocument getOrCreateLinkedDocument(String name) {
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

    public void setMetadataDefaultAnalyzer(String metadataDefaultAnalyzer) {
        this.metadataDefaultAnalyzer = metadataDefaultAnalyzer;
    }

    public Map<String, ConfigMetadataFieldGroup> getMetadataFieldGroups() {
        return metadataFieldGroups;
    }

    void addMetadataFieldGroup(ConfigMetadataFieldGroup g) {
        metadataFieldGroups.put(g.getName(), g);
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public boolean isContentViewable() {
        return contentViewable;
    }

    public void setContentViewable(boolean contentViewable) {
        this.contentViewable = contentViewable;
    }

}