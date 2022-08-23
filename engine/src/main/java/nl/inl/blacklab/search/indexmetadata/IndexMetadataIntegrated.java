package nl.inl.blacklab.search.indexmetadata;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.node.ObjectNode;

import nl.inl.blacklab.forwardindex.AnnotationForwardIndex;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.indexers.config.TextDirection;
import nl.inl.blacklab.search.BlackLabIndex;
import nl.inl.blacklab.search.BlackLabIndexAbstract;
import nl.inl.blacklab.search.BlackLabIndexWriter;
import nl.inl.util.Json;
import nl.inl.util.LuceneUtil;
import nl.inl.util.TimeUtil;

/**
 * Determines the structure of a BlackLab index.
 */
public class IndexMetadataIntegrated implements IndexMetadataWriter {

    private static final Logger logger = LogManager.getLogger(IndexMetadataIntegrated.class);

    /**
     * Manages the index metadata document.
     *
     * We store the metadata in a special document. This class takes care of that.
     */
    private static class MetadataDocument {

        /** How to recognize field related to index metadata */
        private static final String INDEX_METADATA_FIELD_PREFIX = "__index_metadata";

        /** Name in our index for the metadata field (stored in a special document that should never be matched) */
        private static final String METADATA_FIELD_NAME = INDEX_METADATA_FIELD_PREFIX + "__";

        /** Index metadata document gets a marker field so we can find it again (value same as field name) */
        private static final String METADATA_MARKER = INDEX_METADATA_FIELD_PREFIX + "_marker__";

        /** Contents of document format file is written to metadata document. */
        private static final String METADATA_FIELD_DOCUMENT_FORMAT = INDEX_METADATA_FIELD_PREFIX + "_documentFormat__";

        private static final TermQuery METADATA_DOC_QUERY = new TermQuery(new Term(METADATA_MARKER, METADATA_MARKER));

        private static final org.apache.lucene.document.FieldType markerFieldType;

        static {
            markerFieldType = new org.apache.lucene.document.FieldType();
            markerFieldType.setIndexOptions(IndexOptions.DOCS);
            markerFieldType.setTokenized(false);
            markerFieldType.setOmitNorms(true);
            markerFieldType.setStored(false);
            markerFieldType.setStoreTermVectors(false);
            markerFieldType.setStoreTermVectorPositions(false);
            markerFieldType.setStoreTermVectorOffsets(false);
            markerFieldType.freeze();
        }

        private int metadataDocId = -1;

        public ObjectNode readFromIndex(IndexReader reader) {
            try {
                IndexSearcher searcher = new IndexSearcher(reader);
                final List<Integer> docIds = new ArrayList<>();
                searcher.search(METADATA_DOC_QUERY, new LuceneUtil.SimpleDocIdCollector(docIds));
                if (docIds.isEmpty())
                    throw new RuntimeException("No index metadata found!");
                if (docIds.size() > 1)
                    throw new RuntimeException("Multiple index metadata found!");
                metadataDocId = docIds.get(0);
                String indexMetadataJson = reader.document(metadataDocId).get(METADATA_FIELD_NAME);
                ObjectMapper mapper = Json.getJsonObjectMapper();
                return (ObjectNode) mapper.readTree(new StringReader(indexMetadataJson));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public void saveToIndex(BlackLabIndexWriter indexWriter, ObjectNode metadata, String documentFormatConfigFileContents) {
            // Create a metadata document with the metadata JSON, config format file,
            // and a marker field to we can find it again
            Document indexmetadataDoc = new Document();
            ObjectWriter mapper = Json.getJsonObjectMapper().writerWithDefaultPrettyPrinter();
            try {
                String metadataJson = mapper.writeValueAsString(metadata);
                indexmetadataDoc.add(new StoredField(METADATA_FIELD_NAME, metadataJson));
                indexmetadataDoc.add(new StoredField(METADATA_FIELD_DOCUMENT_FORMAT, documentFormatConfigFileContents));
                indexmetadataDoc.add(new org.apache.lucene.document.Field(METADATA_MARKER, METADATA_MARKER, markerFieldType));

                // Update the index metadata by deleting it, then adding a new version.
                indexWriter.writer().updateDocument(METADATA_DOC_QUERY.getTerm(), indexmetadataDoc);

                File debugFileMetadata = new File(indexWriter.indexDirectory(), "debug-metadata.json");
                FileUtils.writeStringToFile(debugFileMetadata, metadataJson, StandardCharsets.UTF_8);
                File debugFileDocumentFormat = new File(indexWriter.indexDirectory(), "debug-format.blf.yaml");
                FileUtils.writeStringToFile(debugFileDocumentFormat, documentFormatConfigFileContents, StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new RuntimeException("Error saving index metadata", e);
            }
        }

        public int getDocumentId() {
            return metadataDocId;
        }

        public boolean isMetadataDocumentField(String name) {
            return name.startsWith(INDEX_METADATA_FIELD_PREFIX); // special fields for index metadata
        }
    }


    /** Our index */
    protected final BlackLabIndex index;

    /** Corpus-level custom metadata */
    private final CustomPropsMap custom = new CustomPropsMap();

    @Override
    public CustomPropsMap custom() {
        return custom;
    }

    /** When BlackLab.jar was built */
    private String blackLabBuildTime;

    /** BlackLab version used to (initially) create index */
    private String blackLabVersion;

    /** Format the index uses */
    private String indexFormat;

    /** Time at which index was created */
    private String timeCreated;

    /** Time at which index was created */
    private String timeModified;

    /** May all users freely retrieve the full content of documents, or is that restricted? */
    private boolean contentViewable = false;

    /**
     * Indication of the document format(s) in this index.
     *
     * This is in the form of a format identifier as understood by the
     * DocumentFormats class (either an abbreviation or a (qualified) class name).
     */
    private String documentFormat;

    protected long tokenCount = 0;

    /** Our metadata fields */
    protected final MetadataFieldsImpl metadataFields;

    /** Our annotated fields */
    protected final AnnotatedFieldsImpl annotatedFields = new AnnotatedFieldsImpl();

    /** Have we determined our tokenCount from the index? (done lazily) */
    private boolean tokenCountCalculated;

    /** How many documents with values for the main annotated field are in our index */
    private int documentCount;

    /** Contents of the documentFormat config file at index creation time. */
    private String documentFormatConfigFileContents = "(not set)";

    private final BlackLabIndexWriter indexWriter;

    private final MetadataDocument metadataDocument = new MetadataDocument();

    /** Is this instance frozen, that is, are all mutations disallowed? */
    private boolean frozen;

    public IndexMetadataIntegrated(BlackLabIndex index, boolean createNewIndex,
            ConfigInputFormat config) {
        this.index = index;
        metadataFields = new MetadataFieldsImpl(createMetadataFieldValuesFactory());

        this.indexWriter = index.indexMode() ? (BlackLabIndexWriter)index : null;
        if (createNewIndex || index.reader().leaves().isEmpty()) {
            // Create new index metadata from config
            File dir = index.indexDirectory();
            ObjectNode metadataObj = IntegratedMetadataUtil.createIndexMetadata(config, dir);
            IntegratedMetadataUtil.extractFromJson(this,  metadataObj, index.reader());
            documentFormatConfigFileContents = config == null ? "(no config)" : config.getOriginalFileContents();
            if (index.indexMode())
                save(); // save debug file if any
        } else {
            // Read previous index metadata from index
            ObjectNode metadataObj = metadataDocument.readFromIndex(index.reader());
            IntegratedMetadataUtil.extractFromJson(this, metadataObj, index.reader());
            detectMainAnnotation(index.reader());

            // we defer counting tokens because we can't always access the
            // forward index while constructing
            tokenCountCalculated = false;
        }

        // For integrated index, because metadata wasn't allowed to change during indexing,
        // return a default field config if you try to get a missing field.
        metadataFields.setThrowOnMissingField(false);
    }

    @Override
    public AnnotatedFieldsImpl annotatedFields() {
        return annotatedFields;
    }

    @Override
    public MetadataFieldsImpl metadataFields() {
        return metadataFields;
    }

    /**
     * Get a description of the index, if specified
     *
     * @return the description
     */
    @Override
    public String description() {
        return custom().get("description", "");
    }

    /**
     * Is the content freely viewable by all users, or is it restricted?
     *
     * @return true if the full content may be retrieved by anyone
     */
    @Override
    public boolean contentViewable() {
        return contentViewable;
    }

    /**
     * What's the text direction of this corpus?
     *
     * @return text direction
     */
    @Override
    public TextDirection textDirection() {
        return TextDirection.fromCode(custom.get("textDirection", "ltr"));
    }

    /**
     * What format(s) is/are the documents in?
     *
     * This is in the form of a format identifier as understood by the
     * DocumentFormats class (either an abbreviation or a (qualified) class name).
     *
     * @return the document format(s)
     */
    @Override
    public String documentFormat() {
        return documentFormat;
    }

    /**
     * What version of the index format is this?
     *
     * @return the index format version
     */
    @Override
    public String indexFormat() {
        return indexFormat;
    }

    /**
     * When was this index created?
     *
     * @return date/time stamp
     */
    @Override
    public String timeCreated() {
        return timeCreated;
    }

    /**
     * When was this index last modified?
     *
     * @return date/time stamp
     */
    @Override
    public String timeModified() {
        return timeCreated;
    }

    /**
     * When was the BlackLab.jar used for indexing built?
     *
     * @return date/time stamp
     */
    @Override
    public String indexBlackLabBuildTime() {
        return blackLabBuildTime;
    }

    /**
     * When was the BlackLab.jar used for indexing built?
     *
     * @return date/time stamp
     */
    @Override
    public String indexBlackLabVersion() {
        return blackLabVersion;
    }

    // Methods that mutate data
    // ------------------------------------

    synchronized AnnotatedFieldImpl getOrCreateAnnotatedField(String name) {
        ensureNotFrozen();
        AnnotatedFieldImpl cfd = null;
        if (annotatedFields.exists(name))
            cfd = ((AnnotatedFieldImpl) annotatedField(name));
        if (cfd == null) {
            cfd = new AnnotatedFieldImpl(this, name);
            annotatedFields.put(name, cfd);
        }
        return cfd;
    }

    /**
     * Indicate that the index was modified, so that fact will be recorded in the
     * metadata file.
     */
    @Override
    public void updateLastModified() {
        ensureNotFrozen();
        timeModified = TimeUtil.timestamp();
    }

    /**
     * While indexing, check if an annotated field is already registered in the
     * metadata, and if not, add it now.
     *
     * @param fieldWriter field to register
     * @return registered annotated field
     */
    @Override
    public synchronized AnnotatedField registerAnnotatedField(AnnotatedFieldWriter fieldWriter) {
        String fieldName = fieldWriter.name();
        AnnotatedFieldImpl cf;
        if (annotatedFields.exists(fieldName)) {
            cf = annotatedFields.get(fieldName);
        } else {
            ensureNotFrozen();

            // Not registered yet; do so now. Note that we only add the main annotation,
            // not the other annotations, but that's okay; they're not needed at index
            // time and will be detected at search time.
            cf = getOrCreateAnnotatedField(fieldName);
        }

        // Make sure all the annotations, their sensitivities, the offset sensitivity, whether
        // they have a forward index, and the main annotation are all registered correctly.
        for (AnnotationWriter annotationWriter: fieldWriter.annotationWriters()) {
            AnnotationImpl annotation = cf.getOrCreateAnnotation(annotationWriter.name());
            for (String suffix: annotationWriter.sensitivitySuffixes()) {
                annotation.addAlternative(MatchSensitivity.fromLuceneFieldSuffix(suffix));
            }
            if (annotationWriter.includeOffsets())
                annotation.setOffsetsSensitivity(MatchSensitivity.fromLuceneFieldSuffix(annotationWriter.mainSensitivity()));
            annotation.setForwardIndex(annotationWriter.hasForwardIndex());
            annotation.createSensitivities(annotationWriter.getSensitivitySetting());
            //annotation.setOffsetsSensitivity(annotation.mainSensitivity().sensitivity());
            annotationWriter.setAnnotation(annotation);
        }
        String mainAnnotName = fieldWriter.mainAnnotation().name();
        cf.getOrCreateAnnotation(mainAnnotName); // create main annotation
        cf.setMainAnnotationName(mainAnnotName); // set main annotation
        fieldWriter.setAnnotatedField(cf);

        return cf;
    }

    /**
     * Set the display name for this index. Only makes sense in index mode where the
     * change will be saved. Usually called when creating an index.
     *
     * @param displayName the display name to set.
     */
    @Override
    public void setDisplayName(String displayName) {
        ensureNotFrozen();
        if (displayName.length() > 80)
            displayName = StringUtils.abbreviate(displayName, 75);
        custom.put("displayName", displayName);
    }

    public void setDescription(String description) {
        ensureNotFrozen();
        custom.put("description", description);
    }

    public void setBlackLabBuildTime(String blackLabBuildTime) {
        ensureNotFrozen();
        this.blackLabBuildTime = blackLabBuildTime;
    }

    public void setBlackLabVersion(String blackLabVersion) {
        ensureNotFrozen();
        this.blackLabVersion = blackLabVersion;
    }

    public void setIndexFormat(String indexFormat) {
        ensureNotFrozen();
        this.indexFormat = indexFormat;
    }

    public void setTimeCreated(String timeCreated) {
        ensureNotFrozen();
        this.timeCreated = timeCreated;
    }

    public void setTimeModified(String timeModified) {
        ensureNotFrozen();
        this.timeModified = timeModified;
    }

    /**
     * Set a document format (or formats) for this index.
     *
     * This should be a format identifier as understood by the DocumentFormats class
     * (either an abbreviation or a (qualified) class name).
     *
     * It only makes sense to call this in index mode, where this change will be
     * saved.
     *
     * @param documentFormat the document format to store
     */
    @Override
    public void setDocumentFormat(String documentFormat) {
        ensureNotFrozen();
        this.documentFormat = documentFormat;
    }

    /**
     * Used when creating an index to initialize contentViewable setting. Do not use
     * otherwise.
     *
     * It is also used to support a deprecated configuration setting in BlackLab
     * Server, but this use will eventually be removed.
     *
     * @param contentViewable whether content may be freely viewed
     */
    @Override
    public void setContentViewable(boolean contentViewable) {
        ensureNotFrozen();
        this.contentViewable = contentViewable;
    }

    /**
     * Used when creating an index to initialize textDirection setting. Do not use
     * otherwise.
     *
     * @param textDirection text direction
     */
    @Override
    public void setTextDirection(TextDirection textDirection) {
        ensureNotFrozen();
        custom.put("textDirection", textDirection.getCode());
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
        String displayName = custom.get("displayName", "");
        return !StringUtils.isEmpty(displayName) ? displayName :
                StringUtils.capitalize(IndexMetadata.indexNameFromDirectory(index.indexDirectory()));
    }

    @Override
    public void freeze() {
        this.frozen = true;
        annotatedFields.freeze();
        metadataFields.freeze();
    }

    @Override
    public boolean isFrozen() {
        return this.frozen;
    }

    protected void detectMainAnnotation(IndexReader reader) {
        if (annotatedFields.main() != null)
            return; // we already know our main annotated field, probably from the metadata

        // Detect main contents field and main annotations of annotated fields
        // Detect the main annotations for all annotated fields
        // (looks for fields with char offset information stored)
        AnnotatedFieldImpl mainAnnotatedField = null;
        for (AnnotatedField d: annotatedFields()) {
            if (mainAnnotatedField == null || d.name().equals("contents"))
                mainAnnotatedField = (AnnotatedFieldImpl) d;

            // We don't know the tokenCount here (always 0 for integrated), so always try to detect.
            // (does this work for empty indexes..?)
            ((AnnotatedFieldImpl) d).detectMainAnnotation(reader);
        }
        annotatedFields.setMainAnnotatedField(mainAnnotatedField);
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

    @Override
    public void save() {
        if (!index.indexMode())
            throw new RuntimeException("Cannot save indexmetadata in search mode!");
        if (indexWriter == null)
            throw new RuntimeException("Cannot save indexmetadata, indexWriter == null");
        ObjectNode metadata = IntegratedMetadataUtil.encodeToJson(this);
        metadataDocument.saveToIndex(indexWriter, metadata, documentFormatConfigFileContents);
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
        MetadataField f = metadataFields.register(fieldName);
        // We don't keep track of metadata field values in the integrated
        // index format because it cannot change during indexing.
        // Instead we will use DocValues to get the field values when necessary.
        f.setKeepTrackOfValues(false);
        return f;
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

    protected MetadataFieldValues.Factory createMetadataFieldValuesFactory() {
        return new MetadataFieldValuesFromIndex.Factory(index);
    }

    @Override
    public int metadataDocId() {
        return metadataDocument.getDocumentId();
    }

}
