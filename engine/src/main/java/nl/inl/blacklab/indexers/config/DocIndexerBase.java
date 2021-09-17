package nl.inl.blacklab.indexers.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntField;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.exceptions.InvalidInputFormatConfig;
import nl.inl.blacklab.exceptions.MalformedInputFile;
import nl.inl.blacklab.exceptions.MaxDocsReached;
import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.DownloadCache;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.MetadataFetcher;
import nl.inl.blacklab.index.annotated.AnnotatedFieldWriter;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.util.FileProcessor;
import nl.inl.util.StringUtil;

public abstract class DocIndexerBase extends DocIndexer {

    private static final boolean TRACE = false;

    /**
     * Position of start tags and their index in the annotation arrays, so we can add
     * payload when we find the end tags
     */
    static final class OpenTagInfo {

        public String name;

        public int index;

        public OpenTagInfo(String name, int index) {
            this.name = name;
            this.index = index;
        }
    }

    /** Annotated fields we're indexing. */
    private Map<String, AnnotatedFieldWriter> annotatedFields = new LinkedHashMap<>();

    /**
     * A field named "contents", or, if that doesn't exist, the first annotated field
     * added.
     */
    private AnnotatedFieldWriter mainAnnotatedField;

    /** The indexing object for the annotated field we're currently processing. */
    private AnnotatedFieldWriter currentAnnotatedField;

    /** The tag annotation for the annotated field we're currently processing. */
    private AnnotationWriter annotStartTag;

    /** The main annotation for the annotated field we're currently processing. */
    private AnnotationWriter annotMain;

    /** The main annotation for the annotated field we're currently processing. */
    private AnnotationWriter annotPunct;

    /**
     * If no punctuation expression is defined, add a space between each word by
     * default.
     */
    private boolean addDefaultPunctuation = true;

    /**
     * If true, the next word gets no default punctuation even if
     * addDefaultPunctuation is true. Useful for implementing glue tag behaviour
     * (Sketch Engine WPL format)
     */
    private boolean preventNextDefaultPunctuation = false;

    /** For capturing punctuation between words. */
    private StringBuilder punctuation = new StringBuilder();

    /**
     * Unique strings we store, so we avoid storing many copies of the same string
     * (e.g. punctuation).
     */
    private Map<String, String> uniqueStrings = new HashMap<>();

    /**
     * If true, we're indexing into an existing Lucene document. Don't overwrite it
     * with a new one.
     */
    private boolean indexingIntoExistingLuceneDoc = false;

    /** Currently opened inline tags we still need to add length payload to */
    private List<OpenTagInfo> openInlineTags = new ArrayList<>();

    /**
     * Store documents? Can be set to false in ConfigInputFormat to if no content
     * store is desired, or via indexSpecificDocument to prevent storing linked
     * documents.
     */
    private boolean storeDocuments = true;

    /**
     * The content store we should store this document in. Also stored the content
     * store id in the field with this name with "Cid" appended, e.g. "metadataCid"
     * if useContentStore equals "metadata". This is used for storing linked
     * document, if desired. Normally null, meaning document should be stored in the
     * default field and content store (usually "contents", with the id in field
     * "contents#cid").
     */
    private String contentStoreName = null;

    /**
     * Total words processed by this indexer. Used for reporting progress, do not
     * reset except when finished with file.
     */
    protected int wordsDone = 0;
    private int wordsDoneAtLastReport = 0;
    private int charsDoneAtLastReport = 0;

    /**
     * External metadata fetcher (if any), responsible for looking up the metadata
     * and adding it to the Lucene document.
     */
    private MetadataFetcher metadataFetcher;

    /**
     * What annotations where skipped because they were not declared?
     */
    Set<String> skippedAnnotations = new HashSet<>();

    protected String getContentStoreName() {
        return contentStoreName;
    }

    protected void addAnnotatedField(AnnotatedFieldWriter field) {
        annotatedFields.put(field.name(), field);
    }

    protected AnnotatedFieldWriter getMainAnnotatedField() {
        if (mainAnnotatedField == null) {
            // The "main annotated field" is the field that stores the document content id for now.
            // (We will change this eventually so the document content id is not stored with a annotated field
            // but as a metadata field instead.)
            // The main annotated field is a field named "contents" or, if that does not exist, the first
            // annotated field
            for (AnnotatedFieldWriter field : annotatedFields.values()) {
                if (mainAnnotatedField == null)
                    mainAnnotatedField = field;
                else if (field.name().equals("contents"))
                    mainAnnotatedField = field;
            }
        }
        return mainAnnotatedField;
    }

    protected AnnotatedFieldWriter getAnnotatedField(String name) {
        return annotatedFields.get(name);
    }

    protected Map<String, AnnotatedFieldWriter> getAnnotatedFields() {
        return Collections.unmodifiableMap(annotatedFields);
    }

    protected void setCurrentAnnotatedFieldName(String name) {
        currentAnnotatedField = getAnnotatedField(name);
        if (currentAnnotatedField == null)
            throw new InvalidInputFormatConfig("Tried to index annotated field " + name
                    + ", but field wasn't created. Likely cause: init() wasn't called. Did you call the base class method in index()?");
        annotStartTag = currentAnnotatedField.tagsAnnotation();
        annotMain = currentAnnotatedField.mainAnnotation();
        annotPunct = currentAnnotatedField.punctAnnotation();
    }

    protected void addStartChar(int pos) {
        currentAnnotatedField.addStartChar(pos);
    }

    protected void addEndChar(int pos) {
        currentAnnotatedField.addEndChar(pos);
    }

    protected AnnotationWriter getAnnotation(String name) {
        return currentAnnotatedField.annotation(name);
    }

    protected int getCurrentTokenPosition() {
        return annotMain.lastValuePosition() + 1;
    }

    protected AnnotationWriter propTags() {
        return annotStartTag;
    }

    protected AnnotationWriter propMain() {
        return annotMain;
    }

    protected AnnotationWriter propPunct() {
        return annotPunct;
    }

    protected void setPreventNextDefaultPunctuation() {
        preventNextDefaultPunctuation = true;
    }

    /**
     * Index a linked document.
     *
     * @param inputFile where the linked document can be found (file or http(s)
     *            reference)
     * @param pathInsideArchive if input file is an archive: the path to the file we
     *            need inside the archive
     * @param documentPath XPath to the specific linked document we need
     * @param inputFormatIdentifier input format of the linked document
     * @param storeWithName if set, store the linked document and store the id to it
     *            in a field with this name with "Cid" (content id) appended to it
     * @throws IOException on error
     */
    protected void indexLinkedDocument(String inputFile, String pathInsideArchive, String documentPath,
            String inputFormatIdentifier, String storeWithName) throws IOException {
        // Fetch the input file (either by downloading it to a temporary location, or opening it from disk)
        File f = resolveFileReference(inputFile);

        // Get the data
        byte[] data;
        String completePath = inputFile;
        if (inputFile.endsWith(".zip") || inputFile.endsWith(".tar") || inputFile.endsWith(".tar.gz")
                || inputFile.endsWith(".tgz")) {
            // It's an archive. Unpack the right file from it.
            completePath += "/" + pathInsideArchive;
            data = FileProcessor.fetchFileFromArchive(f, pathInsideArchive);
        } else {
            // Regular file.
            try (InputStream is = new FileInputStream(f)) {
                data = IOUtils.toByteArray(is);
            } catch (IOException e) {
                throw BlackLabRuntimeException.wrap(e);
            }
        }
        if (data == null) {
            throw new BlackLabRuntimeException("Error reading linked document");
        }

        // Index the data
        try (DocIndexer docIndexer = DocumentFormats.get(inputFormatIdentifier, docWriter, completePath, data,
                Indexer.DEFAULT_INPUT_ENCODING)) {
            if (docIndexer instanceof DocIndexerBase) {
                @SuppressWarnings("resource")
                DocIndexerBase ldi = (DocIndexerBase) docIndexer;
                ldi.indexingIntoExistingLuceneDoc = true;
                ldi.currentLuceneDoc = currentLuceneDoc;
                ldi.metadataFieldValues = metadataFieldValues;
                if (storeWithName != null) {
                    // If specified, store in this content store and under this name instead of the default
                    ldi.contentStoreName = storeWithName;
                }
                ldi.indexSpecificDocument(documentPath);
            } else {
                throw new BlackLabRuntimeException("Linked document indexer must be subclass of DocIndexerBase, but is "
                        + docIndexer.getClass().getName());
            }
        }

    }

    /**
     * Index a specific document.
     *
     * Only supported by DocIndexerBase.
     *
     * @param documentExpr Expression (e.g. XPath) used to find the document to
     *            index in the file
     */
    public abstract void indexSpecificDocument(String documentExpr);

    /**
     * Given a URL or file reference, either download to a temp file or find file
     * and return it.
     *
     * @param inputFile URL or (relative) file reference
     * @return the file
     * @throws IOException
     */
    protected File resolveFileReference(String inputFile) throws IOException {
        if (inputFile.startsWith("http://") || inputFile.startsWith("https://")) {
            return DownloadCache.downloadFile(inputFile);
        }
        if (inputFile.startsWith("file://"))
            inputFile = inputFile.substring(7);
        File f = docWriter.linkedFile(inputFile);
        if (f == null)
            throw new FileNotFoundException("Referenced file not found: " + inputFile);
        if (!f.canRead())
            throw new IOException("Cannot read referenced file: " + f);
        return f;
    }

    /**
     * If we've already seen this string, return the original copy.
     *
     * This prevents us from storing many copies of the same string.
     *
     * @param possibleDupe string we may already have stored
     * @return unique instance of the string
     */
    protected String dedupe(String possibleDupe) {
        String original = uniqueStrings.get(possibleDupe);
        if (original != null)
            return original;
        uniqueStrings.put(possibleDupe, possibleDupe);
        return possibleDupe;
    }

    protected void trace(String msg) {
        if (TRACE)
            System.out.print(msg);
    }

    protected void traceln(String msg) {
        if (TRACE)
            System.out.println(msg);
    }

    protected void setStoreDocuments(boolean storeDocuments) {
        this.storeDocuments = storeDocuments;
    }

    protected boolean isStoreDocuments() {
        return storeDocuments;
    }

    protected void startDocument() {

        traceln("START DOCUMENT");
        if (!indexingIntoExistingLuceneDoc) {
            currentLuceneDoc = new Document();
            addMetadataField("fromInputFile", documentName);
            addMetadataFieldsFromParameters(); // DEPRECATED for these types of indexer, but still supported for now
        }
        if (docWriter != null && !indexingIntoExistingLuceneDoc)
            docWriter.listener().documentStarted(documentName);
    }

    protected void endDocument() {
        traceln("END DOCUMENT");

        for (AnnotatedFieldWriter field : getAnnotatedFields().values()) {
            AnnotationWriter propMain = field.mainAnnotation();

            // Make sure all the annotations have an equal number of values.
            // See what annotation has the highest position
            // (in practice, only starttags and endtags should be able to have
            // a position one higher than the rest)
            int lastValuePos = 0;
            for (AnnotationWriter prop : field.annotationWriters()) {
                if (prop.lastValuePosition() > lastValuePos)
                    lastValuePos = prop.lastValuePosition();
            }

            // Make sure we always have one more token than the number of
            // words, so there's room for any tags after the last word, and we
            // know we should always skip the last token when matching.
            if (propMain.lastValuePosition() == lastValuePos)
                lastValuePos++;

            // Add empty values to all lagging annotations
            for (AnnotationWriter prop : field.annotationWriters()) {
                while (prop.lastValuePosition() < lastValuePos) {
                    prop.addValue("");
                    if (prop.hasPayload())
                        prop.addPayload(null);
                    if (prop == propMain) {
                        field.addStartChar(getCharacterPosition());
                        field.addEndChar(getCharacterPosition());
                    }
                }
            }
            // Store the different annotations of the annotated field that
            // were gathered in lists while parsing.
            field.addToLuceneDoc(currentLuceneDoc);

            // Add the field with all its annotations to the forward index
            addToForwardIndex(field);

        }

        if (isStoreDocuments()) {
            storeDocument();
        }

        if (docWriter != null) {
            // If there's an external metadata fetcher, call it now so it can
            // add the metadata for this document and (optionally) store the
            // metadata
            // document in the content store (and the corresponding id in the
            // Lucene doc)
            MetadataFetcher m = getMetadataFetcher();
            if (m != null) {
                m.addMetadata();
            }

        }

        if (!indexingIntoExistingLuceneDoc)
            addMetadataToDocument();
        try {
            // Add Lucene doc to indexer, if not existing already
            if (docWriter != null && !indexingIntoExistingLuceneDoc)
                docWriter.add(currentLuceneDoc);
        } catch (Exception e) {
            throw BlackLabRuntimeException.wrap(e);
        }

        for (AnnotatedFieldWriter annotatedField : getAnnotatedFields().values()) {
            // Reset annotated field for next document
            // don't reuse buffers, they're still referenced by the lucene doc.
            annotatedField.clear(!indexingIntoExistingLuceneDoc);
        }

        // Report progress
        if (docWriter != null) {
            reportCharsProcessed();
            reportTokensProcessed();
        }
        if (docWriter != null && !indexingIntoExistingLuceneDoc)
            docWriter.listener().documentDone(documentName);

        currentLuceneDoc = null;

        // Stop if required
        if (docWriter != null) {
            if (!docWriter.continueIndexing())
                throw new MaxDocsReached();
        }

        uniqueStrings.clear();
    }

    /**
     * Store the entire document at once.
     *
     * Subclasses that simply capture the entire document can use this in their
     * storeDocument implementation.
     *
     * @param document document to store
     */
    protected void storeWholeDocument(String document) {
        // Finish storing the document in the document store,
        // retrieve the content id, and store that in Lucene.
        // (Note that we do this after adding the "extra closing token", so the character
        // positions for the closing token still make (some) sense)
        String contentIdFieldName;
        String contentStoreName = getContentStoreName();
        if (contentStoreName == null) {
            AnnotatedFieldWriter main = getMainAnnotatedField();
            if (main == null) {
                contentStoreName = "metadata";
                contentIdFieldName = "metadataCid";
            } else {
                contentStoreName = main.name();
                contentIdFieldName = AnnotatedFieldNameUtil.contentIdField(main.name());
            }
        } else {
            contentIdFieldName = contentStoreName + "Cid";
        }
        int contentId = -1;
        if (docWriter != null) {
            ContentStore contentStore = docWriter.contentStore(contentStoreName);
            contentId = contentStore.store(document);
        }
        currentLuceneDoc.add(new IntField(contentIdFieldName, contentId, Store.YES));
    }

    protected void storeWholeDocument(byte[] content, int offset, int length, Charset cs) {
        // Finish storing the document in the document store,
        // retrieve the content id, and store that in Lucene.
        // (Note that we do this after adding the "extra closing token", so the character
        // positions for the closing token still make (some) sense)
        String contentIdFieldName;
        String contentStoreName = getContentStoreName();
        if (contentStoreName == null) {
            AnnotatedFieldWriter main = getMainAnnotatedField();
            if (main == null) {
                contentStoreName = "metadata";
                contentIdFieldName = "metadataCid";
            } else {
                contentStoreName = main.name();
                contentIdFieldName = AnnotatedFieldNameUtil.contentIdField(main.name());
            }
        } else {
            contentIdFieldName = contentStoreName + "Cid";
        }
        int contentId = -1;
        if (docWriter != null) {
            ContentStore contentStore = docWriter.contentStore(contentStoreName);
            contentId = contentStore.store(content, offset, length, cs);
        }
        currentLuceneDoc.add(new IntField(contentIdFieldName, contentId, Store.YES));
    }

    /**
     * Store (or finish storing) the document in the content store.
     *
     * Also set the content id field so we know how to retrieve it later.
     */
    protected abstract void storeDocument();

    protected void inlineTag(String tagName, boolean isOpenTag, Map<String, String> attributes) {
        if (isOpenTag) {
            trace("<" + tagName + ">");

            int lastStartTagPos = propTags().lastValuePosition();
            int currentPos = getCurrentTokenPosition();
            int posIncrement = currentPos - lastStartTagPos;
            propTags().addValue(tagName, posIncrement);
            propTags().addPayload(null);
            int startTagIndex = propTags().lastValueIndex();
            openInlineTags.add(new OpenTagInfo(tagName, startTagIndex));

            for (Entry<String, String> e : attributes.entrySet()) {
                // Index element attribute values
                String name = e.getKey();
                String value = e.getValue();
                propTags().addValue("@" + name.toLowerCase() + "__" + value.toLowerCase(), 0);
                propTags().addPayload(null);
            }

        } else {
            traceln("</" + tagName + ">");

            int currentPos = getCurrentTokenPosition();

            // Add payload to start tag annotation indicating end position
            if (openInlineTags.isEmpty())
                throw new MalformedInputFile("Close tag " + tagName + " found, but that tag is not open");
            OpenTagInfo openTag = openInlineTags.remove(openInlineTags.size() - 1);
            if (!openTag.name.equals(tagName))
                throw new MalformedInputFile(
                        "Close tag " + tagName + " found, but " + openTag.name + " expected");
            byte[] payload = ByteBuffer.allocate(4).putInt(currentPos).array();
            propTags().setPayloadAtIndex(openTag.index, new BytesRef(payload));
        }
    }

    protected void punctuation(String punct) {
        trace(punct);
        punctuation.append(punct);
    }

    protected void setAddDefaultPunctuation(boolean addDefaultPunctuation) {
        this.addDefaultPunctuation = addDefaultPunctuation;
    }

    public boolean shouldAddDefaultPunctuation() {
        return addDefaultPunctuation;
    }

    /**
     * calls {@link #getCharacterPosition()}
     */
    protected void beginWord() {
        addStartChar(getCharacterPosition());
    }

    /**
     * calls {@link #getCharacterPosition()}
     */
    protected void endWord() {
        String punct;
        if (punctuation.length() == 0)
            punct = addDefaultPunctuation && !preventNextDefaultPunctuation ? " " : "";
        else
            punct = punctuation.toString();

        preventNextDefaultPunctuation = false;
        // Normalize once more in case we hit more than one adjacent punctuation
        propPunct().addValue(StringUtil.normalizeWhitespace(punct));
        addEndChar(getCharacterPosition());
        wordsDone++;
        if (wordsDone > 0 && wordsDone % 5000 == 0) {
            reportCharsProcessed();
            reportTokensProcessed();
        }
        if (punctuation.length() > 10_000)
            punctuation = new StringBuilder(); // let's not hold on to this much memory
        else
            punctuation.setLength(0);
    }

    /**
     * Index an annotation.
     *
     * Can be used to add annotation(s) at the current position (indexAtPositions ==
     * null), or to add annotations at specific positions (indexAtPositions contains
     * positions). The latter is used for standoff annotations.
     *
     * Also called for subannotations (with the value already prepared)
     *
     * @param name annotation name
     * @param value annotation value
     * @param increment if indexAtPosition == null: the token increment to use
     * @param indexAtPositions if null: index at the current position; otherwise:
     *            index at these positions
     */
    protected void annotation(String name, String value, int increment, List<Integer> indexAtPositions) {
        AnnotationWriter annotation = getAnnotation(name);
        if (annotation != null) {
            if (indexAtPositions == null) {
                if (name.equals("word"))
                    trace(value + " ");
                annotation.addValue(value, increment);
            } else {
                for (Integer position : indexAtPositions) {
                    annotation.addValueAtPosition(value, position);
                }
            }
        } else {
            // Annotation not declared; report, but keep going
            if (!skippedAnnotations.contains(name)) {
                skippedAnnotations.add(name);
                logger.error(documentName + ": skipping undeclared annotation " + name);
            }
        }
    }

    @Override
    public void addMetadataField(String fieldName, String value) {
        fieldName = optTranslateFieldName(fieldName);
        traceln("METADATA " + fieldName + "=" + value);
        super.addMetadataField(fieldName, value);
    }

    @Override
    public final void reportCharsProcessed() {
        final int charsDone = getCharacterPosition();
        final int charsDoneSinceLastReport;
        if (charsDoneAtLastReport > charsDone)
            charsDoneSinceLastReport = charsDone;
        else
            charsDoneSinceLastReport = charsDone - charsDoneAtLastReport;

        docWriter.listener().charsDone(charsDoneSinceLastReport);
        charsDoneAtLastReport = charsDone;
    }

    /**
     * Report the change in wordsDone since the last report
     */
    @Override
    public final void reportTokensProcessed() {
        final int wordsDoneSinceLastReport;
        if (wordsDoneAtLastReport > wordsDone) // wordsDone reset by child class? report everything then
            wordsDoneSinceLastReport = wordsDone;
        else
            wordsDoneSinceLastReport = wordsDone - wordsDoneAtLastReport;

        docWriter.listener().tokensDone(wordsDoneSinceLastReport);
        wordsDoneAtLastReport = wordsDone;
    }

    /**
     * Get the external metadata fetcher for this indexer, if any.
     *
     * The metadata fetcher can be configured through the "metadataFetcherClass"
     * parameter.
     *
     * @return the metadata fetcher if any, or null if there is none.
     */
    protected MetadataFetcher getMetadataFetcher() {
        if (metadataFetcher == null) {
            @SuppressWarnings("deprecation")
            String metadataFetcherClassName = getParameter("metadataFetcherClass");
            if (metadataFetcherClassName != null) {
                try {
                    Class<? extends MetadataFetcher> metadataFetcherClass = Class.forName(metadataFetcherClassName)
                            .asSubclass(MetadataFetcher.class);
                    Constructor<? extends MetadataFetcher> ctor = metadataFetcherClass.getConstructor(DocIndexer.class);
                    metadataFetcher = ctor.newInstance(this);
                } catch (ReflectiveOperationException e) {
                    throw BlackLabRuntimeException.wrap(e);
                }
            }
        }
        return metadataFetcher;
    }

}
