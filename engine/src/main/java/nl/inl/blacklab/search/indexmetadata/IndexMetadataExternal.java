package nl.inl.blacklab.search.indexmetadata;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.IndexReader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.util.FileUtil;
import nl.inl.util.Json;

public class IndexMetadataExternal extends IndexMetadataAbstract {

    private static final String METADATA_FILE_NAME = "indexmetadata";

    private static final Charset INDEX_STRUCT_FILE_ENCODING = StandardCharsets.UTF_8;

    /** When we save this file, should we write it as json or yaml? */
    private final boolean saveAsJson;

    /** Where to save indexmetadata.json */
    private final File indexDir;

    /**
     * Construct an IndexMetadata object, querying the index for the available
     * fields and their types.
     *
     * @param index the index of which we want to know the structure
     * @param indexDir where the index (and the metadata file) is stored
     * @param createNewIndex whether we're creating a new index
     * @param config input format config to use as template for index structure /
     *            metadata (if creating new index)
     * @throws IndexVersionMismatch if the index is too old or too new to be opened by this BlackLab version
     */
    public IndexMetadataExternal(BlackLabIndex index, File indexDir, boolean createNewIndex,
            ConfigInputFormat config) throws IndexVersionMismatch {
        super(index);
        this.indexDir = indexDir;

        // Find existing metadata file, if any.
        File metadataFile = FileUtil.findFile(List.of(this.indexDir), IndexMetadataExternal.METADATA_FILE_NAME,
                Arrays.asList("json", "yaml", "yml"));

        saveAsJson = false;
        if (createNewIndex) {
            if (metadataFile != null) {
                // Don't leave the old metadata file if we're creating a new index
                if (metadataFile.exists() && !metadataFile.delete())
                    throw new BlackLabRuntimeException("Could not delete file: " + metadataFile);
            }

            // Always write a .yaml file for new index
            metadataFile = new File(this.indexDir, IndexMetadataExternal.METADATA_FILE_NAME + ".yaml");
        }

        if (createNewIndex && config != null) {
            // Create an index metadata file from this config.
            ObjectNode jsonRoot = createIndexMetadataFromConfig(config);
            extractFromJson(jsonRoot, null, true);
            save();
        } else {
            // Read existing metadata or create empty new one
            readOrCreateMetadata(this.index.reader(), createNewIndex, metadataFile, false);
        }
    }

    /**
     * Construct an IndexMetadata object, querying the index for the available
     * fields and their types.
     *
     * @param index the index of which we want to know the structure
     * @param indexDir where the index (and the metadata file) is stored
     * @param createNewIndex whether we're creating a new index
     * @param indexTemplateFile JSON file to use as template for index structure /
     *            metadata (if creating new index)
     * @throws IndexVersionMismatch if the index is too old or too new to be opened by this BlackLab version
     */
    public IndexMetadataExternal(BlackLabIndex index, File indexDir, boolean createNewIndex, File indexTemplateFile)
            throws IndexVersionMismatch {
        super(index);
        this.indexDir = indexDir;
        // Find existing metadata file, if any.
        File metadataFile = FileUtil.findFile(List.of(this.indexDir), IndexMetadataExternal.METADATA_FILE_NAME,
                Arrays.asList("yaml", "yml", "json"));
        if (metadataFile != null && createNewIndex) {
            // Don't leave the old metadata file if we're creating a new index
            if (!metadataFile.delete())
                throw new BlackLabRuntimeException("Could not delete file: " + metadataFile);
        }

        // If none found, or creating new index: metadata file should be same format as template.
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
            metadataFile = new File(this.indexDir, IndexMetadataExternal.METADATA_FILE_NAME + "." + templateExt);
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

        readOrCreateMetadata(this.index.reader(), createNewIndex, metadataFile, usedTemplate);
    }

    private void readOrCreateMetadata(IndexReader reader, boolean createNewIndex, File metadataFile,
            boolean usedTemplate) throws IndexVersionMismatch {
        ensureNotFrozen();

        // Read and interpret index metadata file
        ObjectNode jsonRoot;
        if ((createNewIndex && !usedTemplate) || !metadataFile.exists()) {
            // No metadata file yet; start with a blank one
            jsonRoot = createEmptyIndexMetadata();
            usedTemplate = false;
        } else {
            // Read the metadata file
            jsonRoot = readMetadataFile(metadataFile);
        }
        extractFromJson(jsonRoot, reader, usedTemplate);
        if (!createNewIndex) { // new index doesn't have this information yet
            detectMainAnnotation(reader);
        }
    }

    private ObjectNode readMetadataFile(File metadataFile) {
        ObjectNode jsonRoot;
        try {
            boolean isJson = metadataFile.getName().endsWith(".json");
            ObjectMapper mapper = isJson ? Json.getJsonObjectMapper() : Json.getYamlObjectMapper();
            jsonRoot = (ObjectNode) mapper.readTree(metadataFile);
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
        return jsonRoot;
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

    @Override
    public void freezeBeforeIndexing() {
        // don't freeze; with this index format, it was traditionally allowed
        // for the metadata to change during indexing, because it is re-saved at
        // the end.
    }

    @Override
    protected String getLatestIndexFormat() {
        /**
         * The latest index format. Written to the index metadata file.
         *
         * - 3: first version to include index metadata file
         * - 3.1: tag length in payload
         */
        return "3.1";
    }
}
