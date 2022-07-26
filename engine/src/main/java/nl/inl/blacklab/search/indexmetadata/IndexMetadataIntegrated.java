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
    private static final String METADATA_FIELD_NAME = "__index_metadata__";

    /** Index metadata document get a marker field so we can find it again (value same as field name) */
    private static final String METADATA_MARKER = "__index_metadata_marker__";

    private static final Query METADATA_DOC_QUERY = new TermQuery(new Term(METADATA_MARKER, METADATA_MARKER));

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
        this.debugFile = new File(index.indexDirectory(), "integrated-meta-debug.yaml");

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
                detectMainAnnotation(index.reader());
            } catch (IOException e) {
                throw new RuntimeException("Error finding index metadata doc", e);
            }
        }
    }

    private ObjectNode getMetadataFromIndex(BlackLabIndex index) throws IOException {
        IndexSearcher searcher = new IndexSearcher(index.reader());
        final List<Integer> docIds = new ArrayList<>();
        searcher.search(METADATA_DOC_QUERY, new LuceneUtil.SimpleDocIdCollector(docIds));
        if (docIds.isEmpty())
            throw new RuntimeException("No index metadata found!");
        if (docIds.size() > 1)
            throw new RuntimeException("Multiple index metadata found!");
        int docId = docIds.get(0);
        String indexMetadataYaml = index.reader().document(docId).get(METADATA_FIELD_NAME);
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
            indexmetadataDoc.add(new StoredField(METADATA_FIELD_NAME, sw.toString()));
            indexmetadataDoc.add(new org.apache.lucene.document.Field(METADATA_MARKER, METADATA_MARKER, markerFieldType));

            // Update the index metadata by deleting it, then adding a new version.
            // TODO: probably use versioning to prevent losing indexmetadata if we crash
            //   (i.e. add new document, then remove old document by searching with the previous version)
            indexWriter.writer().deleteDocuments(METADATA_DOC_QUERY);
            indexWriter.writer().addDocument(indexmetadataDoc);

            if (debugFile != null)
                FileUtils.writeStringToFile(debugFile, sw.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Error saving index metadata", e);
        }
    }

}
