package nl.inl.blacklab.index.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.IntField;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.externalstorage.ContentStore;
import nl.inl.blacklab.index.DocIndexer;
import nl.inl.blacklab.index.DocIndexerFactory;
import nl.inl.blacklab.index.DocumentFormats;
import nl.inl.blacklab.index.Indexer;
import nl.inl.blacklab.index.InputFormatException;
import nl.inl.blacklab.index.complex.ComplexField;
import nl.inl.blacklab.index.complex.ComplexFieldProperty;
import nl.inl.blacklab.index.complex.ComplexFieldUtil;
import nl.inl.blacklab.search.indexstructure.IndexStructure;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc;
import nl.inl.blacklab.search.indexstructure.MetadataFieldDesc.UnknownCondition;
import nl.inl.util.ExUtil;
import nl.inl.util.FileProcessor;
import nl.inl.util.FileProcessor.FileHandler;

public abstract class DocIndexerBase extends DocIndexer {

    private static final boolean TRACE = false;

    /** Position of start tags and their index in the property arrays, so we can add payload when we find the end tags */
    static final class OpenTagInfo {

        public String name;

        public int position;

        public int index;

        public OpenTagInfo(String name, int position, int index) {
            this.name = name;
            this.position = position;
            this.index = index;
        }
    }

    /** File handler that reads a single file into a byte array. */
    static final class FetchFileHandler implements FileHandler {

        private final String pathToFile;

        byte[] bytes;

        FetchFileHandler(String pathInsideArchive) {
            this.pathToFile = pathInsideArchive;
        }

        @Override
        public void stream(String path, InputStream f) {
            if (path.equals(pathToFile)) {
                try {
                    bytes = IOUtils.toByteArray(f);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public void file(String path, File f) {
            throw new UnsupportedOperationException();
        }
    }

    public static void closeAllZips() {
        synchronized (openZips) {
            // We don't close linked document zips immediately; closing them when you're likely to
            // reuse them soon is inefficient.
            // (we should probably keep track of last access and close them eventually, though)
            for (Entry<File, ZipFile> entry: openZips.entrySet()) {
                try {
                    entry.getValue().close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                openZips.remove(entry.getKey());
            }
        }
    }

    public static ZipFile openZip(File zipFile) throws IOException {
        synchronized (openZips) {
            ZipFile z = openZips.get(zipFile);
            if (z == null) {
                z = new ZipFile(zipFile);
                openZips.put(zipFile, z);
            }
            return z;
        }
    }

    private static byte[] fetchFileFromArchive(File f, final String pathInsideArchive) {
        if (f.getName().endsWith(".gz") || f.getName().endsWith(".tgz")) {
            // We have to process the whole file, we can't do random access.
            FileProcessor proc = new FileProcessor(false, true);
            FetchFileHandler fileHandler = new FetchFileHandler(pathInsideArchive);
            proc.setFileHandler(fileHandler);
            try {
                proc.processFile(f);
                return fileHandler.bytes;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } else if (f.getName().endsWith(".zip")) {
            // We can do random access. Fetch the file we want.
            try {
                ZipFile z = openZip(f);
                ZipEntry e = z.getEntry(pathInsideArchive);
                InputStream is = z.getInputStream(e);
                return IOUtils.toByteArray(is);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported archive type: " + f.getName());
        }
    }

    /**
     * Download file to temp file if it hasn't been downloaded already.
     *
     * @param inputFile URL of the file
     * @return temp file
     * @throws IOException
     * @throws MalformedURLException
     */
    private static File downloadFile(String inputFile) throws IOException, MalformedURLException {
        synchronized (downloadedFiles) {
            File tempFile = downloadedFiles.get(inputFile);
            if (tempFile == null) {
                String ext = inputFile.replaceAll("^.+(\\.[^\\.]+)$", "$1");
                if (ext == null || ext.isEmpty())
                    ext = ".xml";
                tempFile = File.createTempFile("BlackLab_download_", ext);
                tempFile.deleteOnExit();
                FileUtils.copyURLToFile(new URL(inputFile), tempFile);
                downloadedFiles.put(inputFile, tempFile);
            }
            return tempFile;
        }
    }

    /** Zip files opened by DocIndexerBase indexers. Should be closed eventually. */
    private static Map<File, ZipFile> openZips = new LinkedHashMap<>();

    /** Files we've downloaded to a temp dir. Will be deleted on exit. */
    static Map<String, File> downloadedFiles = new HashMap<>();

    /** Complex fields we're indexing. */
    private Map<String, ComplexField> complexFields = new LinkedHashMap<>();

    /** A field named "contents", or, if that doesn't exist, the first complex field added. */
    private ComplexField mainComplexField;

    /** The indexing object for the complex field we're currently processing. */
    private ComplexField currentComplexField;

    /** The tag property for the complex field we're currently processing. */
    private ComplexFieldProperty propStartTag;

    /** The main property for the complex field we're currently processing. */
    private ComplexFieldProperty propMain;

    /** The main property for the complex field we're currently processing. */
    private ComplexFieldProperty propPunct;

    /** If no punctuation expression is defined, add a space between each word by default. */
    private boolean addDefaultPunctuation = true;

    /** If true, the next word gets no default punctuation even if addDefaultPunctuation is true.
     *  Useful for implementing glue tag behaviour (Sketch Engine WPL format) */
    private boolean preventNextDefaultPunctuation = false;

    /** For capturing punctuation between words. */
    private StringBuilder punctuation = new StringBuilder();

    /** Unique strings we store, so we avoid storing many copies of the same string (e.g. punctuation). */
    private Map<String, String> uniqueStrings = new HashMap<>();

    /** If true, we're indexing into an existing Lucene document. Don't overwrite it with a new one. */
    private boolean indexingIntoExistingLuceneDoc = false;

    /** Currently opened inline tags we still need to add length payload to */
    private List<OpenTagInfo> openInlineTags = new ArrayList<>();

    /** Store documents? Can be set to false in ConfigInputFormat to if no content store is desired,
     *  or via indexSpecificDocument to prevent storing linked documents. */
    private boolean storeDocuments = true;

    /** The content store we should store this document in. Also stored the content store id
     *  in the field with this name with "Cid" appended, e.g. "metadataCid" if useContentStore
     *  equals "metadata". This is used for storing linked document, if desired.
     *  Normally null, meaning document should be stored in the default field and content
     *  store (usually "contents", with the id in field "contents#cid"). */
    private String contentStoreName = null;

    protected String getContentStoreName() {
        return contentStoreName;
    }

    protected void addComplexField(ComplexField complexField) {
        complexFields.put(complexField.getName(), complexField);
    }

    protected ComplexField getMainComplexField() {
        if (mainComplexField == null) {
            // The "main complex field" is the field that stores the document content id for now.
            // (We will change this eventually so the document content id is not stored with a complex field
            // but as a metadata field instead.)
            // The main complex field is a field named "contents" or, if that does not exist, the first
            // complex field
            for (ComplexField complexField: complexFields.values()) {
                if (mainComplexField == null)
                    mainComplexField = complexField;
                else if (complexField.getName().equals("contents"))
                    mainComplexField = complexField;
            }
        }
        return mainComplexField;
    }

    protected ComplexField getComplexField(String name) {
        return complexFields.get(name);
    }

    protected Map<String, ComplexField> getComplexFields() {
        return Collections.unmodifiableMap(complexFields);
    }

    protected void setCurrentComplexField(String name) {
        currentComplexField = getComplexField(name);
        if (currentComplexField == null)
            throw new InputFormatConfigException("Tried to index complex field " + name + ", but field wasn't created. Likely cause: init() wasn't called. Did you call the base class method in index()?");
        propStartTag = currentComplexField.getTagProperty();
        propMain = currentComplexField.getMainProperty();
        propPunct = currentComplexField.getPunctProperty();
    }

    protected void addStartChar(int pos) {
        currentComplexField.addStartChar(pos);
    }

    protected void addEndChar(int pos) {
        currentComplexField.addEndChar(pos);
    }

    protected ComplexFieldProperty getProperty(String name) {
        return currentComplexField.getProperty(name);
    }

    protected int getCurrentTokenPosition() {
        return propMain.lastValuePosition() + 1;
    }

    protected ComplexFieldProperty propTags() {
        return propStartTag;
    }

    protected ComplexFieldProperty propMain() {
        return propMain;
    }

    protected ComplexFieldProperty propPunct() {
        return propPunct;
    }

    protected void setPreventNextDefaultPunctuation() {
        preventNextDefaultPunctuation = true;
    }

    /**
     * Index a linked document.
     *
     * @param inputFile where the linked document can be found (file or http(s) reference)
     * @param pathInsideArchive if input file is an archive: the path to the file we need inside the archive
     * @param documentPath XPath to the specific linked document we need
     * @param inputFormat input format of the linked document
     * @param storeWithName if set, store the linked document and store the id to it in a field with this name with "Cid" (content id) appended to it
     * @throws IOException on error
     */
    protected void indexLinkedDocument(String inputFile, String pathInsideArchive, String documentPath, String inputFormat, String storeWithName) throws IOException {
        // Get the DocIndexerFactory
        DocIndexerFactory docIndexerFactory = DocumentFormats.getIndexerFactory(inputFormat);

        // Fetch the input file (either by downloading it to a temporary location, or opening it from disk)
        File f = resolveFileReference(inputFile);

        // Get the data
        byte [] data;
        String completePath = inputFile;
        if (inputFile.endsWith(".zip") || inputFile.endsWith(".tar") || inputFile.endsWith(".tar.gz") || inputFile.endsWith(".tgz")) {
            // It's an archive. Unpack the right file from it.
            completePath += "/" + pathInsideArchive;
            data = fetchFileFromArchive(f, pathInsideArchive);
        } else {
            // Regular file.
            try(InputStream is = new FileInputStream(f)) {
                data = IOUtils.toByteArray(is);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        if (data == null) {
            throw new RuntimeException("Error reading linked document");
        }

        // Index the data
        DocIndexer docIndexer = docIndexerFactory.get(indexer, completePath, data, Indexer.DEFAULT_INPUT_ENCODING);
        if (docIndexer instanceof DocIndexerBase) {
            DocIndexerBase ldi = (DocIndexerBase)docIndexer;
            ldi.indexingIntoExistingLuceneDoc = true;
            ldi.currentLuceneDoc = currentLuceneDoc;
            if (storeWithName != null)
                ldi.contentStoreName = storeWithName;
            else
                ldi.storeDocuments = false;
            try {
                ldi.indexSpecificDocument(documentPath);
            } catch (Exception e) {
                throw ExUtil.wrapRuntimeException(e);
            } finally {
                ldi.indexingIntoExistingLuceneDoc = false;
                ldi.currentLuceneDoc = null;
                ldi.contentStoreName = null;
                ldi.storeDocuments = true;
            }
        } else {
            throw new RuntimeException("Linked document indexer must be subclass of DocIndexerBase, but is " + docIndexer.getClass().getName());
        }

    }

    /**
     * Index a specific document.
     *
     * Only supported by DocIndexerBase.
     *
     * @param documentExpr Expression (e.g. XPath) used to find the document to index in the file
     */
    public abstract void indexSpecificDocument(String documentExpr);

    /**
     * Given a URL or file reference, either download to a temp file or find file and return it.
     *
     * @param inputFile URL or (relative) file reference
     * @return the file
     * @throws IOException
     */
    protected File resolveFileReference(String inputFile) throws IOException {
        if (inputFile.startsWith("http://") || inputFile.startsWith("https://")) {
            return downloadFile(inputFile);
        }
        if (inputFile.startsWith("file://"))
            inputFile = inputFile.substring(7);
        if (indexer == null) // TEST
            return new File(inputFile);
        File f = indexer.getLinkedFile(inputFile);
        if (f == null)
            throw new FileNotFoundException("References file not found: " + f);
        if (!f.canRead())
            throw new IOException("Cannot read referenced file " + f);
        return f;
    }

    /** If we've already seen this string, return the original copy.
     *
     * This prevents us from storing many copies of the same string.
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

    protected boolean getStoreDocuments() {
        return storeDocuments;
    }

    protected void startDocument() {

        traceln("START DOCUMENT");
        if (!indexingIntoExistingLuceneDoc) {
            currentLuceneDoc = new Document();
            if (indexer != null)
                currentLuceneDoc.add(new Field("fromInputFile", documentName, indexer.getMetadataFieldType(false)));

            // DEPRECATED for these types of indexer, but still supported for now
            addMetadataFieldsFromParameters();
        }
        if (indexer != null)
            indexer.getListener().documentStarted(documentName);
    }

    protected void endDocument() {
        traceln("END DOCUMENT");

        for (ComplexField complexField: getComplexFields().values()) {
            ComplexFieldProperty propMain = complexField.getMainProperty();

            // Make sure all the properties have an equal number of values.
            // See what property has the highest position
            // (in practice, only starttags and endtags should be able to have
            // a position one higher than the rest)
            int lastValuePos = 0;
            for (ComplexFieldProperty prop: complexField.getProperties()) {
                if (prop.lastValuePosition() > lastValuePos)
                    lastValuePos = prop.lastValuePosition();
            }

            // Make sure we always have one more token than the number of
            // words, so there's room for any tags after the last word, and we
            // know we should always skip the last token when matching.
            if (propMain.lastValuePosition() == lastValuePos)
                lastValuePos++;

            // Add empty values to all lagging properties
            for (ComplexFieldProperty prop: complexField.getProperties()) {
                while (prop.lastValuePosition() < lastValuePos) {
                    prop.addValue("");
                    if (prop.hasPayload())
                        prop.addPayload(null);
                    if (prop == propMain) {
                        complexField.addStartChar(getCharacterPosition());
                        complexField.addEndChar(getCharacterPosition());
                    }
                }
            }
            // Store the different properties of the complex field that
            // were gathered in lists while parsing.
//            System.out.println("Adding to lucene doc: " + complexField.getName());
//            System.out.println("Values: " + complexField.getMainProperty().getValues());
            complexField.addToLuceneDoc(currentLuceneDoc);

            // Add all properties to forward index
            for (ComplexFieldProperty prop: complexField.getProperties()) {
                if (!prop.hasForwardIndex())
                    continue;

                // Add property (case-sensitive tokens) to forward index and add
                // id to Lucene doc
                String propName = prop.getName();
                String fieldName = ComplexFieldUtil.propertyField(
                        complexField.getName(), propName);
                if (indexer != null) {
                    int fiid = indexer.addToForwardIndex(fieldName, prop);
                    currentLuceneDoc.add(new IntField(ComplexFieldUtil
                            .forwardIndexIdField(fieldName), fiid, Store.YES));
                }
            }

        }

        if (getStoreDocuments()) {
            storeDocument();
        }

        if (indexer != null) {
            // See what metadatafields are missing or empty and add unknown value
            // if desired.
            IndexStructure struct = indexer.getSearcher().getIndexStructure();
            for (String fieldName: struct.getMetadataFields()) {
                MetadataFieldDesc fd = struct.getMetadataFieldDesc(fieldName);
                boolean missing = false, empty = false;
                String currentValue = currentLuceneDoc.get(fieldName);
                if (currentValue == null)
                    missing = true;
                else if (currentValue.length() == 0)
                    empty = true;
                UnknownCondition cond = fd.getUnknownCondition();
                boolean useUnknownValue = false;
                switch (cond) {
                case EMPTY:
                    useUnknownValue = empty;
                    break;
                case MISSING:
                    useUnknownValue = missing;
                    break;
                case MISSING_OR_EMPTY:
                    useUnknownValue = missing | empty;
                    break;
                case NEVER:
                    useUnknownValue = false;
                    break;
                }
                if (useUnknownValue)
                    addMetadataField(optTranslateFieldName(fieldName), fd.getUnknownValue());
            }
        }

        try {
            // Add Lucene doc to indexer
            if (indexer != null)
                indexer.add(currentLuceneDoc);
        } catch (Exception e) {
            throw ExUtil.wrapRuntimeException(e);
        }

        for (ComplexField complexField: getComplexFields().values()) {
            // Reset complex field for next document
            complexField.clear();
        }

        // Report progress
        if (indexer != null) {
            indexer.getListener().charsDone(getCharacterPosition());
            reportTokensProcessed(wordsDone);
        }
        wordsDone = 0;
        if (indexer != null)
            indexer.getListener().documentDone(documentName);

        currentLuceneDoc = null;

        // Stop if required
        if (indexer != null) {
            if (!indexer.continueIndexing())
                throw new MaxDocsReachedException();
        }

        uniqueStrings.clear();
    }

    /**
     * Store the entire document at once.
     *
     * Subclasses that simply capture the entire document can use this in their storeDocument implementation.
     * @param document document to store
     */
    protected void storeWholeDocument(String document) {
        // Finish storing the document in the document store,
        // retrieve the content id, and store that in Lucene.
        // (Note that we do this after adding the dummy token, so the character
        // positions for the dummy token still make (some) sense)
        String contentIdFieldName;
        String contentStoreName = getContentStoreName();
        if (contentStoreName == null) {
            ComplexField main = getMainComplexField();
            if (main == null) {
                contentStoreName = "metadata";
                contentIdFieldName = "metadataCid";
            } else {
                contentStoreName = main.getName();
                contentIdFieldName = ComplexFieldUtil.contentIdField(main.getName());
            }
        } else {
            contentIdFieldName = contentStoreName + "Cid";
        }
        int contentId = -1;
        if (indexer != null) {
            ContentStore contentStore = indexer.getContentStore(contentStoreName);
            contentId = contentStore.store(document);
        }
        currentLuceneDoc.add(new IntField(contentIdFieldName, contentId, Store.YES));
    }

    /**
     * Store (or finish storing) the document in the content store.
     *
     * Also set the content id field so we know how to retrieve it later.
     */
    protected abstract void storeDocument();

    /**
     * Translate a field name before adding it to the Lucene document.
     *
     * By default, simply returns the input. May be overridden to change
     * the name of a metadata field as it is indexed.
     *
     * @param from original metadata field name
     * @return new name
     */
    protected String optTranslateFieldName(String from) {
        return from;
    }

    protected void inlineTag(String tagName, boolean isOpenTag, Map<String, String> attributes) {
        if (isOpenTag) {
            trace("<" + tagName + ">");

            int lastStartTagPos = propTags().lastValuePosition();
            int currentPos = getCurrentTokenPosition();
            int posIncrement = currentPos - lastStartTagPos;
            propTags().addValue(tagName, posIncrement);
            propTags().addPayload(null);
            int startTagIndex = propTags().getLastValueIndex();
            openInlineTags.add(new OpenTagInfo(tagName, currentPos, startTagIndex));

            for (Entry<String, String> e: attributes.entrySet()) {
                // Index element attribute values
                String name = e.getKey();
                String value = e.getValue();
                propTags().addValue("@" + name.toLowerCase() + "__" + value.toLowerCase(), 0);
                propTags().addPayload(null);
            }

        } else {
            traceln("</" + tagName + ">");

            int currentPos = getCurrentTokenPosition();

            // Add payload to start tag property indicating end position
            if (openInlineTags.size() == 0)
                throw new InputFormatException("Close tag " + tagName + " found, but that tag is not open");
            OpenTagInfo openTag = openInlineTags.remove(openInlineTags.size() - 1);
            if (!openTag.name.equals(tagName))
                throw new InputFormatException("Close tag " + tagName + " found, but " + openTag.name + " expected");
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

    protected void beginWord() {
        int pos = getCharacterPosition();
        addStartChar(pos);
    }

    protected void endWord() {
        String punct;
        if (punctuation.length() == 0)
            punct = addDefaultPunctuation && !preventNextDefaultPunctuation ? " " : "";
        else
            punct = punctuation.toString();
        preventNextDefaultPunctuation = false;
        propPunct().addValue(punct);
        addEndChar(getCharacterPosition());
        wordsDone++;
        if (punctuation.length() > 10000)
            punctuation = new StringBuilder(); // let's not hold on to this much memory
        else
            punctuation.setLength(0);
    }

    /**
     * Index an annotation.
     *
     * Can be used to add annotation(s) at the current position (indexAtPositions == null),
     * or to add annotations at specific positions (indexAtPositions contains positions). The latter
     * is used for standoff annotations.
     *
     * Also called for subannotations (with the value already prepared)
     *
     * @param name annotation name
     * @param value annotation value
     * @param increment if indexAtPosition == null: the token increment to use
     * @param indexAtPositions if null: index at the current position; otherwise: index at these positions
     */
    protected void annotation(String name, String value, int increment, List<Integer> indexAtPositions) {
        ComplexFieldProperty property = getProperty(name);
        if (indexAtPositions == null) {
            if (name.equals("word"))
                trace(value + " ");
//          if (name.equals("xmlid"))
//              trace(value + " ");
            property.addValue(value, increment);
            //System.out.println("Field " + currentComplexField.getName() + ", property " + property.getName() + ": added " + value + ", lastValuePos = " + property.lastValuePosition());
        } else {
//            if (name.equals("rating"))
//                traceln("{" + value + "}: " + indexAtPositions);
            //System.out.println("Field " + currentComplexField.getName() + ", property " + property.getName() + ", value " + value + " (STANDOFF)");
            for (Integer position: indexAtPositions) {
                property.addValueAtPosition(value, position);
                //System.out.println("  added at position " + position);
            }
        }
    }

    /**
     * Index a subannotation.
     *
     * Can be used to add subannotation(s) at the current position (indexAtPositions == null),
     * or to add annotations at specific positions (indexAtPositions contains positions). The latter
     * is used for standoff annotations.
     *
     * @param mainName annotation name
     * @param subName subannotation name
     * @param value annotation value
     * @param indexAtPositions if null: index at the current position; otherwise: index at these positions
     */
    protected void subAnnotation(String mainName, String subName, String value, List<Integer> indexAtPositions) {
        String sep = ComplexFieldUtil.SUBPROPERTY_SEPARATOR;
        String newVal = sep + subName + sep + value;
        annotation(mainName, newVal, 0, indexAtPositions); // increment 0 because we don't want to advance to the next token yet
    }

    @Override
    public void addMetadataField(String fieldName, String value) {
        fieldName = optTranslateFieldName(fieldName);
        traceln("METADATA " + fieldName + "=" + value);
        super.addMetadataField(fieldName, value);
    }

}