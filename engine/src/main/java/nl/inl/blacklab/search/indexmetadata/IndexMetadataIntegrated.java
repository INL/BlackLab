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
import org.apache.lucene.search.TermQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.exceptions.IndexVersionMismatch;
import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.util.Json;
import nl.inl.util.LuceneUtil;

public class IndexMetadataIntegrated extends IndexMetadataAbstract {

    /** How to recognize field related to index metadata */
    private static final String INDEX_METADATA_FIELD_PREFIX = "__index_metadata";

    /** Name in our index for the metadata field (stored in a special document that should never be matched) */
    private static final String METADATA_FIELD_NAME = INDEX_METADATA_FIELD_PREFIX + "__";

    /** Index metadata document get a marker field so we can find it again (value same as field name) */
    private static final String METADATA_MARKER = INDEX_METADATA_FIELD_PREFIX + "_marker__";

    private static final TermQuery METADATA_DOC_QUERY = new TermQuery(new Term(METADATA_MARKER, METADATA_MARKER));

    /** Have we determined our tokenCount from the index? (done lazily) */
    private boolean tokenCountCalculated;

    /** How many documents with values for the main annotated field are in our index */
    private int documentCount;

    /** For writing indexmetadata to disk for debugging */
    private File debugFile = null;

    private final BlackLabIndexWriter indexWriter;

    private final FieldType markerFieldType;

    private int metadataDocId = -1;

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
        if (createNewIndex || index.reader().leaves().isEmpty()) {
            // Create new index metadata from config
            ObjectNode rootNode = config == null ? createEmptyIndexMetadata() : createIndexMetadataFromConfig(config);
            extractFromJson(rootNode, index.reader(), false);
            if (index.indexMode())
                save(); // save debug file if any
        } else {
            // Read previous index metadata from index
            readMetadataFromIndex();

            // we defer counting tokens because we can't always access the
            // forward index while constructing
            tokenCountCalculated = false;
        }

        // For integrated index, because metadata wasn't allowed to change during indexing,
        // return a default field config if you try to get a missing field.
        metadataFields.setThrowOnMissingField(false);
    }

    @Override
    protected boolean includeLegacyValues() {
        return false;
    }

    @Override
    protected boolean makeImplicitSettingsExplicit() {
        return true;
    }

    @Override
    public synchronized long tokenCount() {
        ensureDocsAndTokensCounted();
        return tokenCount;
    }

    @Override
    public synchronized int documentCount() {
        ensureDocsAndTokensCounted();
        return documentCount;
    }

    private synchronized void ensureDocsAndTokensCounted() {
        if (!tokenCountCalculated) {
            tokenCountCalculated = true;
            tokenCount = documentCount = 0;
            if (!isNewIndex()) {
                // Add up token counts for all the documents
                AnnotatedField field = annotatedFields().main();
                Annotation annot = field.mainAnnotation();
                AnnotationForwardIndex afi = index.forwardIndex(field).get(annot);
                index.forEachDocument((__, docId) -> {
                    int docLength = afi.docLength(docId);
                    if (docLength >= 1) {
                        // Positive docLength means that this document has a value for this annotated field
                        // (e.g. the index metadata document does not and returns 0)
                        tokenCount += docLength - BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
                        documentCount++;
                    }
                });
            }
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
        try {
            IndexSearcher searcher = new IndexSearcher(index.reader());
            final List<Integer> docIds = new ArrayList<>();
            searcher.search(METADATA_DOC_QUERY, new LuceneUtil.SimpleDocIdCollector(docIds));
            if (docIds.isEmpty())
                throw new RuntimeException("No index metadata found!");
            if (docIds.size() > 1)
                throw new RuntimeException("Multiple index metadata found!");
            metadataDocId = docIds.get(0);
            String indexMetadataYaml = index.reader().document(metadataDocId).get(METADATA_FIELD_NAME);
            ObjectMapper mapper = Json.getYamlObjectMapper();
            ObjectNode yamlRoot = (ObjectNode) mapper.readTree(new StringReader(indexMetadataYaml));
            extractFromJson(yamlRoot, index.reader(), false);
            detectMainAnnotation(index.reader());
        } catch (IOException|IndexVersionMismatch e) {
            throw new RuntimeException(e);
        }

        /*
        // Get the FieldsProducer from the first index segment
        LeafReaderContext lrc = index.reader().leaves().get(0);
        BlackLab40PostingsReader fieldsProducer = BlackLab40PostingsReader.get(lrc);

        // Read the metadata from the segment info attributes and extract it
        String indexMetadataSerialized = fieldsProducer.getIndexMetadata();
        ObjectMapper mapper = Json.getYamlObjectMapper();
        try {
            ObjectNode yamlRoot = (ObjectNode) mapper.readTree(new StringReader(indexMetadataSerialized));
            extractFromJson(yamlRoot, index.reader(), false);
        } catch (IOException | IndexVersionMismatch e) {
            throw new RuntimeException(e);
        }
        detectMainAnnotation(index.reader());

         */
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
            indexWriter.writer().updateDocument(METADATA_DOC_QUERY.getTerm(), indexmetadataDoc);

            if (debugFile != null)
                FileUtils.writeStringToFile(debugFile, sw.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Error saving index metadata", e);
        }

        /*
        // We don't write the index metadata here, that happens for each segment in BlackLab40PostingsWriter.
        // We do write the debug file if requested though.
        if (debugFile != null) {
            ObjectMapper mapper = Json.getYamlObjectMapper();
            StringWriter sw = new StringWriter();
            try {

                mapper.writeValue(sw, encodeToJson());
                FileUtils.writeStringToFile(debugFile, sw.toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Error saving index metadata", e);
            }
        }
        */
    }

    @Override
    public void freezeBeforeIndexing() {
        // Contrary to the "classic" index format, with this one the metadata
        // cannot change while indexing. So freeze it now to enforce that.
        if (!isFrozen())
            freeze();
    }

    @Override
    public void addToTokenCount(long tokensProcessed) {
        // We don't keep the token count in the metadata in the integrated
        // index format because it cannot change during indexing.
        // However, we do want to keep track of it while creating or appending.
        // We just don't check that the metadata isn't frozen (we don't care what value gets written there)
        tokenCount += tokensProcessed;
    }

    @Override
    public synchronized MetadataField registerMetadataField(String fieldName) {
        MetadataField f = super.registerMetadataField(fieldName);
        // We don't keep track of metadata field values in the integrated
        // index format because it cannot change during indexing.
        // Instead we will use DocValues to get the field values when necessary.
        f.setKeepTrackOfValues(false);
        return f;
    }

    protected boolean shouldDetectMainAnnotation() {
        // We don't know the tokenCount here (always 0 for integrated), so always try to detect.
        // (does this work for empty indexes..?)
        return true;
    }

    /**
     * Is this a new, empty index?
     *
     * An empty index is one that doesn't have a main contents field yet.
     *
     * @return true if it is, false if not.
     */
    @Override
    public boolean isNewIndex() {
        // An empty index only contains the index metadata document
        return index.reader().numDocs() <= 1;
    }

    @Override
    protected String getLatestIndexFormat() {
        /**
         * The latest index format. Written to the index metadata file.
         *
         * - 3: first version to include index metadata file
         * - 3.1: tag length in payload
         * - 4: integrated index format (final value for indexFormat; Lucene codec name+version contains
         *      accurate version info now and determines what class is used to read it)
         */
        return "4";
    }

    @Override
    protected MetadataFieldValues.Factory getMetadataFieldValuesFactory() {
        return new MetadataFieldValuesFromIndex.Factory(index);
    }

    @Override
    protected boolean skipMetadataFieldDuringDetection(String name) {
        return name.startsWith(INDEX_METADATA_FIELD_PREFIX); // special fields for index metadata
    }

    @Override
    public int metadataDocId() {
        return metadataDocId;
    }
}
