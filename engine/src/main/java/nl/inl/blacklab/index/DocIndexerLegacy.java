package nl.inl.blacklab.index;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Map;

import org.apache.lucene.document.Field;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.index.annotated.AnnotationWriter;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.util.CountingReader;
import nl.inl.util.UnicodeStream;

/**
 * Abstract base class for a DocIndexer processing XML files.
 */
public abstract class DocIndexerLegacy extends DocIndexerAbstract {
    /**
     * Write content chunks per 10M (i.e. don't keep all content in memory at all
     * times)
     */
    private static final long WRITE_CONTENT_CHUNK_SIZE = 10_000_000;

    protected final boolean skippingCurrentDocument = false;

    protected CountingReader reader;

    /**
     * Total words processed by this indexer. Used for reporting progress, do not
     * reset except when finished with file.
     */
    protected int wordsDone = 0;
    private int wordsDoneAtLastReport = 0;

    //protected ContentStore contentStore;

    private final StringBuilder content = new StringBuilder();

    /** Are we capturing the content of the document for indexing? */
    private boolean captureContent = false;

    /** What field we're capturing content for */
    private String captureContentFieldName;

    private int charsContentAlreadyStored = 0;

    protected final int nDocumentsSkipped = 0;

    public void startCaptureContent(String fieldName) {
        captureContent = true;
        captureContentFieldName = fieldName;

        // Empty the StringBuilder object
        content.setLength(0);
    }

    public int storeCapturedContent() {
        captureContent = false;
        int id = -1;
        if (!skippingCurrentDocument) {
            ContentStore contentStore = getDocWriter().contentStore(captureContentFieldName);
            id = contentStore.store(content.toString());
        }
        content.setLength(0);
        charsContentAlreadyStored = 0;
        return id;
    }

    public void storePartCapturedContent() {
        charsContentAlreadyStored += content.length();
        if (!skippingCurrentDocument) {
            ContentStore contentStore = getDocWriter().contentStore(captureContentFieldName);
            contentStore.storePart(content.toString());
        }
        content.setLength(0);
    }

    private void appendContentInternal(String str) {
        content.append(str);
    }

    public void appendContent(String str) {
        appendContentInternal(str);
        if (content.length() >= WRITE_CONTENT_CHUNK_SIZE) {
            storePartCapturedContent();
        }
    }

    public void appendContent(char[] buffer, int start, int length) {
        appendContentInternal(new String(buffer, start, length));
        if (content.length() >= WRITE_CONTENT_CHUNK_SIZE) {
            storePartCapturedContent();
        }
    }

    public void processContent(String contentToProcess) {
        if (captureContent)
            appendContent(contentToProcess);
    }

    /**
     * Returns the current position in the original XML content in chars.
     * 
     * @return the current char position
     */
    @Override
    protected int getCharacterPosition() {
        return charsContentAlreadyStored + content.length();
    }

    public DocIndexerLegacy(DocWriter indexer, String fileName, Reader reader) {
        setDocWriter(indexer);
        setDocumentName(fileName);
        setDocument(reader);
    }

    /**
     * Set the document to index.
     * 
     * @param reader document
     */
    public void setDocument(Reader reader) {
        this.reader = new CountingReader(reader);
    }

    /**
     * Set the document to index.
     *
     * @param is document contents
     * @param cs charset to use if no BOM found, or null for the default (utf-8)
     */
    public void setDocument(InputStream is, Charset cs) {
        try {
            UnicodeStream unicodeStream = new UnicodeStream(is, cs);
            Charset detectedCharset = unicodeStream.getEncoding();
            setDocument(new InputStreamReader(unicodeStream, detectedCharset));
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    /**
     *
     * Set the document to index.
     *
     * @param contents document contents
     * @param cs charset to use if no BOM found, or null for the default (utf-8)
     */
    public void setDocument(byte[] contents, Charset cs) {
        setDocument(new ByteArrayInputStream(contents), cs);
    }

    /**
     * Set the document to index.
     *
     * @param file file to index
     * @param charset charset to use if no BOM found, or null for the default
     *            (utf-8)
     * @throws FileNotFoundException if not found
     */
    public void setDocument(File file, Charset charset) throws FileNotFoundException {
        setDocument(new FileInputStream(file), charset);
    }

    @Override
    public void close() throws BlackLabRuntimeException {
        try {
            reader.close();
        } catch (IOException e) {
            throw BlackLabRuntimeException.wrap(e);
        }
    }

    @Override
    public final void reportCharsProcessed() {
        long charsProcessed = reader.getCharsReadSinceLastCall();
        getDocWriter().listener().charsDone(charsProcessed);
    }

    /**
     * Report the change in wordsDone since the last report
     */
    @Override
    public final void reportTokensProcessed() {
        int wordsDoneSinceLastReport = 0;

        if (wordsDoneAtLastReport > wordsDone) // reset by child class?
            wordsDoneSinceLastReport = wordsDone;
        else
            wordsDoneSinceLastReport = wordsDone - wordsDoneAtLastReport;

        tokensDone(wordsDoneSinceLastReport);
        wordsDoneAtLastReport = wordsDone;
    }

    /**
     * If the supplied class has a static getDisplayName() method, call it.
     *
     * @param docIndexerClass class to get the display name for
     * @return display name, or empty string if method not found
     */
    public static String getDisplayName(Class<? extends DocIndexer> docIndexerClass) {
        try {
            Method m = docIndexerClass.getMethod("getDisplayName");
            return (String) m.invoke(null);
        } catch (ReflectiveOperationException e) {
            return "";
        }
    }

    /**
     * If the supplied class has a static getDescription() method, call it.
     *
     * @param docIndexerClass class to get the description for
     * @return description, or empty string if method not found
     */
    public static String getDescription(Class<? extends DocIndexer> docIndexerClass) {
        try {
            Method m = docIndexerClass.getMethod("getDescription");
            return (String) m.invoke(null);
        } catch (ReflectiveOperationException e) {
            return "";
        }
    }

    /**
     * Should this docIndexer implementation be listed?
     *
     * A DocIndexer can be hidden by implementing a a static function named
     * isVisible, returning false.
     *
     * @return true if the format should be listed, false if it should be omitted.
     *         Defaults to true when the DocIndexer does not implement the method.
     */
    public static boolean isVisible(Class<? extends DocIndexer> docIndexerClass) {
        try {
            Method m = docIndexerClass.getMethod("isVisible");
            return (boolean) m.invoke(null);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    @Deprecated
    public AnnotationWriter.SensitivitySetting getSensitivitySetting(String annotationName) {
        // See if it's specified in a parameter
        String strSensitivity = getParameter(annotationName + "_sensitivity");
        if (strSensitivity != null) {
            if (strSensitivity.equals("i"))
                return AnnotationWriter.SensitivitySetting.ONLY_INSENSITIVE;
            if (strSensitivity.equals("s"))
                return AnnotationWriter.SensitivitySetting.ONLY_SENSITIVE;
            if (strSensitivity.equals("si") || strSensitivity.equals("is"))
                return AnnotationWriter.SensitivitySetting.SENSITIVE_AND_INSENSITIVE;
            if (strSensitivity.equals("all"))
                return AnnotationWriter.SensitivitySetting.CASE_AND_DIACRITICS_SEPARATE;
        }

        // Not in parameter (or unrecognized value), use default based on
        // annotationName
        if (annotationName.equals(AnnotatedFieldNameUtil.getDefaultMainAnnotationName())
                || annotationName.equals(AnnotatedFieldNameUtil.LEMMA_ANNOT_NAME)) {
            // Word: default to sensitive/insensitive
            return AnnotationWriter.SensitivitySetting.SENSITIVE_AND_INSENSITIVE;
        }
        if (annotationName.equals(AnnotatedFieldNameUtil.PUNCTUATION_ANNOT_NAME)) {
            // Punctuation: default to only insensitive
            return AnnotationWriter.SensitivitySetting.ONLY_INSENSITIVE;
        }
        if (annotationName.equals(AnnotatedFieldNameUtil.TAGS_ANNOT_NAME)) {
            // XML tag properties: default to only sensitive
            return AnnotationWriter.SensitivitySetting.ONLY_SENSITIVE;
        }

        // Unrecognized; default to only insensitive
        return AnnotationWriter.SensitivitySetting.ONLY_INSENSITIVE;
    }

    /**
     * Set a number of parameters for this indexer
     *
     * @param param the parameter names and values
     * @deprecated use a DocIndexerConfig-based indexer
     */
    @Deprecated
    public void setParameters(Map<String, String> param) {
        parameters.putAll(param);
    }

    /**
     * Get a parameter that was set for this indexer
     *
     * @param name parameter name
     * @param defaultValue parameter default value
     * @return the parameter value (or the default value if it was not specified)
     * @deprecated use ConfigInputFormat, IndexMetadata
     */
    @Deprecated
    public String getParameter(String name, String defaultValue) {
        String value = parameters.get(name);
        if (value == null)
            return defaultValue;
        return value;
    }

    /**
     * Get a parameter that was set for this indexer
     *
     * @param name parameter name
     * @return the parameter value (or null if it was not specified)
     * @deprecated use a DocIndexerConfig-based indexer
     */
    @Deprecated
    public String getParameter(String name) {
        return getParameter(name, null);
    }

    /**
     * Get a parameter that was set for this indexer
     *
     * @param name parameter name
     * @param defaultValue parameter default value
     * @return the parameter value (or the default value if it was not specified)
     * @deprecated use a DocIndexerConfig-based indexer
     */
    @Deprecated
    public boolean getParameter(String name, boolean defaultValue) {
        String value = parameters.get(name);
        if (value == null)
            return defaultValue;
        value = value.trim().toLowerCase();
        return value.equals("true") || value.equals("1") || value.equals("yes");
    }

    /**
     * Get a parameter that was set for this indexer
     *
     * @param name parameter name
     * @param defaultValue parameter default value
     * @return the parameter value (or the default value if it was not specified)
     * @deprecated use a DocIndexerConfig-based indexer
     */
    @Deprecated
    public int getParameter(String name, int defaultValue) {
        String value = parameters.get(name);
        if (value == null)
            return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Set the DocWriter object.
     *
     * We use this to add documents to the index.
     *
     * Called by Indexer when the DocIndexer is instantiated.
     *
     * @param docWriter our DocWriter object
     */
    @Override
    public void setDocWriter(DocWriter docWriter) {
        super.setDocWriter(docWriter);
        if (docWriter != null) {
            // Get our parameters from the indexer
            Map<String, String> indexerParameters = docWriter.indexerParameters();
            if (indexerParameters != null)
                setParameters(indexerParameters);
        }
    }

    /**
     * If any metadata fields were supplied in the indexer parameters, add them now.
     *
     * NOTE: we always add these untokenized (because they're usually just
     * indications of which data set a set of files belongs to), but that means they
     * don't get lowercased or de-accented. Because metadata queries are always
     * desensitized, you can't use uppercase or accented letters in these values or
     * they will never be found. This should be addressed.
     *
     * NOTE2: This way of adding metadata values is deprecated. Use an input format config
     * file instead, and configure a field with a fixed value.
     */
    protected void addMetadataFieldsFromParameters() {
        for (Map.Entry<String, String> e: parameters.entrySet()) {
            if (e.getKey().startsWith("meta-")) {
                String fieldName = e.getKey().substring(5);
                String fieldValue = e.getValue();
                currentLuceneDoc.add(new Field(fieldName, fieldValue, getDocWriter().metadataFieldType(false)));
            }
        }
    }
}
