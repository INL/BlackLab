package nl.inl.blacklab.index;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;

import nl.inl.blacklab.contentstore.ContentStore;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.util.CountingReader;

/**
 * Abstract base class for a DocIndexer processing XML files.
 */
public abstract class DocIndexerAbstract extends DocIndexer {
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

    public void processContent(char[] buffer, int start, int length) {
        if (captureContent)
            appendContent(buffer, start, length);
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

    /**
     * NOTE: newer DocIndexers should only have a default constructor, and provide
     * methods to set the Indexer object and the document being indexed (which are
     * called by the Indexer). This allows us more flexibility in how we supply the
     * document to this object (e.g. as a file, a byte array, an inputstream, a
     * reader, ...), which helps if we want to use e.g. VTD-XML and could allow us
     * to re-use DocIndexers in the future.
     */
    public DocIndexerAbstract() {
    }

    public DocIndexerAbstract(DocWriter indexer, String fileName, Reader reader) {
        setDocWriter(indexer);
        setDocumentName(fileName);
        setDocument(reader);
    }

    /**
     * Set the document to index.
     * 
     * @param reader document
     */
    @Override
    @Deprecated
    public void setDocument(Reader reader) {
        this.reader = new CountingReader(reader);
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
     * @param docIndexerClass
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
}
