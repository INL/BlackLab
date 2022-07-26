package nl.inl.blacklab.search.indexmetadata;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.util.Json;
import nl.inl.util.LuceneUtil;

public class IndexMetadataIntegrated extends IndexMetadataAbstract {

    /** Name in our index for the metadata field (stored in a special document that should never be matched) */
    public static final String INDEX_METADATA_FIELD_NAME = "__index_metadata__";

    /** Index metadata document get a marker field so we can find it again (value same as field name) */
    public static final String INDEX_METADATA_MARKER = "__index_metadata_marker__";

    /** For writing indexmetadata to disk for debugging */
    private final File debugFile;

    private final BlackLabIndexWriter indexWriter;

    private final FieldType markerFieldType;

    public IndexMetadataIntegrated(BlackLabIndex index, boolean createNewIndex,
            ConfigInputFormat config) throws IndexVersionMismatch {
        super(index);

        markerFieldType = new FieldType();
        markerFieldType.setIndexOptions(IndexOptions.DOCS);
        markerFieldType.setTokenized(false);
        markerFieldType.setOmitNorms(true);
        markerFieldType.setStored(false);
        markerFieldType.setStoreTermVectors(false);
        markerFieldType.setStoreTermVectorPositions(false);
        markerFieldType.setStoreTermVectorOffsets(false);
        markerFieldType.freeze();


        this.indexWriter = index.indexMode() ? (BlackLabIndexWriter)index : null;
        this.debugFile = new File(index.indexDirectory(), "integrated-meta.yaml");

        if (createNewIndex) {
            // Create new index metadata from config
            ObjectNode rootNode = config == null ? createEmptyIndexMetadata() : createIndexMetadataFromConfig(config);
            extractFromJson(rootNode, index.reader(), false);
            save();
        } else {
            // Read previous index metadata from index
            try {
                ObjectNode yamlRoot = getMetadataFromIndex(index);
                extractFromJson(yamlRoot, index.reader(), false);
            } catch (IOException e) {
                throw new RuntimeException("Error finding index metadata doc", e);
            }
        }
    }

    private ObjectNode getMetadataFromIndex(BlackLabIndex index) throws IOException {
        Query query = new TermQuery(new Term(INDEX_METADATA_MARKER, INDEX_METADATA_MARKER));
        final List<Integer> docIds = new ArrayList<>();
        IndexSearcher searcher = new IndexSearcher(index.reader());
        searcher.search(query, new LuceneUtil.SimpleDocIdCollector(docIds));
        if (docIds.isEmpty())
            throw new RuntimeException("No index metadata found!");
        if (docIds.size() > 1)
            throw new RuntimeException("Multiple index metadata found!");
        int docId = docIds.get(0);
        String indexMetadataYaml = index.reader().document(docId).get(INDEX_METADATA_FIELD_NAME);
        ObjectMapper mapper = Json.getYamlObjectMapper();
        return (ObjectNode) mapper.readTree(new StringReader(indexMetadataYaml));
    }

    @Override
    public void save() {
        if (!index.indexMode())
            throw new RuntimeException("Cannot save indexmetadata in search mode!");
        if (indexWriter == null)
            throw new RuntimeException("Cannot save indexmetadata, indexWriter == null");
        Document indexmetadataDoc = new Document();
        ObjectMapper mapper = Json.getYamlObjectMapper();
        StringWriter sw = new StringWriter();
        try {
            mapper.writeValue(sw, encodeToJson());
            indexmetadataDoc.add(new StoredField(INDEX_METADATA_FIELD_NAME, sw.toString()));
            indexmetadataDoc.add(new org.apache.lucene.document.Field(INDEX_METADATA_MARKER, INDEX_METADATA_MARKER, markerFieldType));
            indexWriter.writer().addDocument(indexmetadataDoc);
            if (debugFile != null)
                FileUtils.writeStringToFile(debugFile, sw.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Error saving index metadata", e);
        }
    }

}
