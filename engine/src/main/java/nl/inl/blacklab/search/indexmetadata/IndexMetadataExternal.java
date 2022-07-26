package nl.inl.blacklab.search.indexmetadata;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.IndexReader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.indexers.config.ConfigCorpus;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.util.FileUtil;
import nl.inl.util.Json;

public class IndexMetadataExternal extends IndexMetadataImpl {

    static final String METADATA_FILE_NAME = "indexmetadata";

    private static final Charset INDEX_STRUCT_FILE_ENCODING = StandardCharsets.UTF_8;

    /** When we save this file, should we write it as json or yaml? */
    private boolean saveAsJson;

    /** Where to save indexmetadata.json */
    private File indexDir;

    public IndexMetadataExternal(BlackLabIndex index, File indexDir, boolean createNewIndex,
            ConfigInputFormat config) throws IndexVersionMismatch {
        super(index, indexDir, createNewIndex, config);
    }

    public IndexMetadataExternal(BlackLabIndex index, File indexDir, boolean createNewIndex, File indexTemplateFile)
            throws IndexVersionMismatch {
        super(index, indexDir, createNewIndex, indexTemplateFile);
    }

    @Override
    protected void initMetadata(File indexDir, boolean createNewIndex, ConfigInputFormat config)
            throws IndexVersionMismatch {
        this.indexDir = indexDir;

        // Find existing metadata file, if any.
        File metadataFile = FileUtil.findFile(List.of(indexDir), IndexMetadataExternal.METADATA_FILE_NAME,
                Arrays.asList("json", "yaml", "yml"));
        if (metadataFile != null && createNewIndex) {
            // Don't leave the old metadata file if we're creating a new index
            if (metadataFile.exists() && !metadataFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + metadataFile);
        }

        // If none found, or creating new index: write a .yaml file.
        if (createNewIndex || metadataFile == null) {
            metadataFile = new File(indexDir, IndexMetadataExternal.METADATA_FILE_NAME + ".yaml");
        }
        saveAsJson = false;
        if (createNewIndex && config != null) {

            // Create an index metadata file from this config.
            ConfigCorpus corpusConfig = config.getCorpusConfig();
            ObjectMapper mapper = Json.getJsonObjectMapper();
            ObjectNode jsonRoot = mapper.createObjectNode();
            String displayName = corpusConfig.getDisplayName();
            if (displayName.isEmpty())
                displayName = determineIndexName();
            jsonRoot.put("displayName", displayName);
            jsonRoot.put("description", corpusConfig.getDescription());
            jsonRoot.put("contentViewable", corpusConfig.isContentViewable());
            jsonRoot.put("textDirection", corpusConfig.getTextDirection().getCode());
            jsonRoot.put("documentFormat", config.getName());
            addVersionInfo(jsonRoot);
            ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
            fieldInfo.put("defaultAnalyzer", config.getMetadataDefaultAnalyzer());
            fieldInfo.put("unknownCondition", config.getMetadataDefaultUnknownCondition().stringValue());
            fieldInfo.put("unknownValue", config.getMetadataDefaultUnknownValue());
            for (Map.Entry<String, String> e: corpusConfig.getSpecialFields().entrySet()) {
                fieldInfo.put(e.getKey(), e.getValue());
            }
            ArrayNode metaGroups = fieldInfo.putArray("metadataFieldGroups");
            ObjectNode annotGroups = fieldInfo.putObject("annotationGroups");
            ObjectNode metadata = fieldInfo.putObject("metadataFields");
            ObjectNode annotated = fieldInfo.putObject("complexFields");

            addFieldInfoFromConfig(metadata, annotated, metaGroups, annotGroups, config);
            extractFromJson(jsonRoot, null, true);
            save();
        } else {
            // Read existing metadata or create empty new one
            readOrCreateMetadata(index.reader(), createNewIndex, metadataFile, false);
        }
    }

    @Override
    protected void initMetadata(File indexDir, boolean createNewIndex, File indexTemplateFile)
            throws IndexVersionMismatch {
        this.indexDir = indexDir;

        // Find existing metadata file, if any.
        File metadataFile = FileUtil.findFile(List.of(indexDir), IndexMetadataExternal.METADATA_FILE_NAME,
                Arrays.asList("json", "yaml", "yml"));
        if (metadataFile != null && createNewIndex) {
            // Don't leave the old metadata file if we're creating a new index
            if (!metadataFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + metadataFile);
        }

        // If none found, or creating new index: metadata file should be same format as
        // template.
        if (createNewIndex || metadataFile == null) {
            // No metadata file yet, or creating a new index;
            // use same metadata format as the template
            boolean templateIsJson = indexTemplateFile != null && indexTemplateFile.getName().endsWith(".json");
            String templateExt = templateIsJson ? "json" : "yaml";
            if (createNewIndex && metadataFile != null) {
                // We're creating a new index, but also found a previous metadata file.
                // Is it a different format than the template? If so, we would end up
                // with two metadata files, which is confusing and might lead to errors.
                boolean existingIsJson = metadataFile.getName().endsWith(".json");
                if (existingIsJson != templateIsJson) {
                    // Delete the existing, different-format file to avoid confusion.
                    if (!metadataFile.delete())
                        throw new BlackLabRuntimeException("Could not delete file: " + metadataFile);
                }
            }
            metadataFile = new File(indexDir, IndexMetadataExternal.METADATA_FILE_NAME + "." + templateExt);
        }
        saveAsJson = metadataFile.getName().endsWith(".json");
        boolean usedTemplate = false;
        if (createNewIndex && indexTemplateFile != null) {
            // Copy the template file to the index dir and read the metadata again.
            try {
                String fileContents = FileUtils.readFileToString(indexTemplateFile, INDEX_STRUCT_FILE_ENCODING);
                FileUtils.write(metadataFile, fileContents, INDEX_STRUCT_FILE_ENCODING);
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
            usedTemplate = true;
        }

        readOrCreateMetadata(index.reader(), createNewIndex, metadataFile, usedTemplate);
    }

    private void readOrCreateMetadata(IndexReader reader, boolean createNewIndex, File metadataFile,
            boolean usedTemplate) throws IndexVersionMismatch {
        ensureNotFrozen();

        // Read and interpret index metadata file
        if ((createNewIndex && !usedTemplate) || !metadataFile.exists()) {
            // No metadata file yet; start with a blank one
            ObjectMapper mapper = Json.getJsonObjectMapper();
            ObjectNode jsonRoot = mapper.createObjectNode();
            jsonRoot.put("displayName", determineIndexName());
            jsonRoot.put("description", "");
            addVersionInfo(jsonRoot);
            ObjectNode fieldInfo = jsonRoot.putObject("fieldInfo");
            fieldInfo.putObject("metadataFields");
            fieldInfo.putObject("complexFields");
            extractFromJson(jsonRoot, reader, false);
        } else {
            // Read the metadata file
            try {
                boolean isJson = metadataFile.getName().endsWith(".json");
                ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
                ObjectNode jsonRoot = (ObjectNode) mapper.readTree(metadataFile);
                extractFromJson(jsonRoot, reader, usedTemplate);
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        }

        // Detect main contents field and main annotations of annotated fields
        if (!createNewIndex) { // new index doesn't have this information yet
            // Detect the main annotations for all annotated fields
            // (looks for fields with char offset information stored)
            AnnotatedFieldImpl mainContentsField = null;
            for (AnnotatedField d: annotatedFields()) {
                if (mainContentsField == null || d.name().equals("contents"))
                    mainContentsField = (AnnotatedFieldImpl) d;
                if (tokenCount() > 0) // no use trying this on an empty index
                    ((AnnotatedFieldImpl) d).detectMainAnnotation(reader);
            }
            annotatedFields.setMainContentsField(mainContentsField);
        }
    }

    protected String determineIndexName() {
        ensureNotFrozen();
        String name = indexDir.getName();
        if (name.equals("index"))
            name = indexDir.getAbsoluteFile().getParentFile().getName();
        return name;
    }

    /**
     * Get the display name for the index.
     *
     * If no display name was specified, returns the name of the index directory.
     *
     * @return the display name
     */
    @Override
    public String displayName() {
        String dispName = "index";
        if (displayName != null && displayName.length() != 0)
            dispName = displayName;
        if (dispName.equalsIgnoreCase("index"))
            dispName = StringUtils.capitalize(indexDir.getName());
        if (dispName.equalsIgnoreCase("index"))
            dispName = StringUtils.capitalize(indexDir.getAbsoluteFile().getParentFile().getName());
        return dispName;
    }

    @Override
    public void save() {
        String ext = saveAsJson ? ".json" : ".yaml";
        File metadataFile = new File(indexDir, IndexMetadataExternal.METADATA_FILE_NAME + ext);
        try {
            boolean isJson = metadataFile.getName().endsWith(".json");
            ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
            mapper.writeValue(metadataFile, encodeToJson());
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

}
