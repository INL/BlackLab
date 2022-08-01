package nl.inl.blacklab.search.indexmetadata;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.codec.BlackLab40PostingsReader;
import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.util.Json;

public class IndexMetadataIntegrated extends IndexMetadataAbstract {

    /** Name in our index for the metadata field (stored in a special document that should never be matched) */
    private static final String METADATA_FIELD_NAME = "__index_metadata__";

    /** Index metadata document get a marker field so we can find it again (value same as field name) */
    private static final String METADATA_MARKER = "__index_metadata_marker__";

    private static final TermQuery METADATA_DOC_QUERY = new TermQuery(new Term(METADATA_MARKER, METADATA_MARKER));

    /** For writing indexmetadata to disk for debugging */
    private File debugFile = null;

//    private final BlackLabIndexWriter indexWriter;

//    private final FieldType markerFieldType;

    public IndexMetadataIntegrated(BlackLabIndex index, String yaml) {
        super(index);

        try {
            ObjectMapper mapper = Json.getYamlObjectMapper();
            extractFromJson((ObjectNode)mapper.readTree(yaml), index.reader(), false);
            detectMainAnnotation(index.reader());
        } catch (IndexVersionMismatch e) {
            throw new RuntimeException(e);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public IndexMetadataIntegrated(BlackLabIndex index, boolean createNewIndex,
            ConfigInputFormat config) throws IndexVersionMismatch {
        super(index);

        this.debugFile = new File(index.indexDirectory(), "integrated-meta-debug.yaml");
        if (createNewIndex) {
            // Create new index metadata from config
            ObjectNode rootNode = config == null ? createEmptyIndexMetadata() : createIndexMetadataFromConfig(config);
            extractFromJson(rootNode, index.reader(), false);
            save();
        } else {
            // Read previous index metadata from index
            readMetadataFromIndex();
        }
    }

    public String serialize() {
        ObjectMapper mapper = Json.getYamlObjectMapper();
        StringWriter sw = new StringWriter();
        try {
            mapper.writeValue(sw, encodeToJson());
            return sw.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void readMetadataFromIndex() {
        // Get the FieldsProducer from the first index segment
        LeafReaderContext lrc = index.reader().leaves().get(0);
        BlackLab40PostingsReader fieldsProducer = BlackLab40PostingsReader.get(lrc);

        // Read the metadata from the segment info attributes and extract it
        String indexMetadataSerialized = fieldsProducer.getIndexMetadata();
        ObjectMapper mapper = Json.getYamlObjectMapper();
        try {
            ObjectNode yamlRoot = (ObjectNode) mapper.readTree(new StringReader(indexMetadataSerialized));
            extractFromJson(yamlRoot, index.reader(), false);
        } catch (IOException|IndexVersionMismatch e) {
            throw new RuntimeException(e);
        }
        detectMainAnnotation(index.reader());
    }

    @Override
    public void save() {
        if (!index.indexMode())
            throw new RuntimeException("Cannot save indexmetadata in search mode!");

        // We don't write the index metadata here, that happens for each segment in BlackLab40PostingsWriter.
        // We do write the debug file if requested though.
        if (debugFile != null) {
            /*
            if (indexWriter == null)
                throw new RuntimeException("Cannot save indexmetadata, indexWriter == null");
            Document indexmetadataDoc = new Document();*/
            ObjectMapper mapper = Json.getYamlObjectMapper();
            StringWriter sw = new StringWriter();
            try {

                mapper.writeValue(sw, encodeToJson());
                /*
                indexmetadataDoc.add(new StoredField(METADATA_FIELD_NAME, sw.toString()));
                indexmetadataDoc.add(new org.apache.lucene.document.Field(METADATA_MARKER, METADATA_MARKER, markerFieldType));

                // Update the index metadata by deleting it, then adding a new version.
                indexWriter.updateDocument(METADATA_DOC_QUERY.getTerm(), indexmetadataDoc);
                */

                FileUtils.writeStringToFile(debugFile, sw.toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Error saving index metadata", e);
            }
        }
    }

    @Override
    public void freezeBeforeIndexing() {
        if (!isFrozen())
            freeze();
    }

    @Override
    public void addToTokenCount(long tokensProcessed) {
        // ignore (immutable metadata)
    }

    @Override
    public synchronized MetadataField registerMetadataField(String fieldName) {
        MetadataField f = super.registerMetadataField(fieldName);
        f.setKeepTrackOfValues(false); // we'll use docvalues instead
        return f;
    }
}
