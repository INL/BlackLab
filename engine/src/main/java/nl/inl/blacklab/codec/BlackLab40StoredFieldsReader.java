package nl.inl.blacklab.codec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.StoredFieldVisitor;
import org.apache.lucene.util.Accountable;

import nl.inl.blacklab.search.BlackLabIndexIntegrated;

/**
 * Provides random access to values stored as a content store.
 * Delegates non-content-store reads to the default implementation.
 */
public class BlackLab40StoredFieldsReader extends StoredFieldsReader {

    /**  */
    private static String fieldNameForCodecAccess;

    /**
     * Get the BlackLab40StoredFieldsReader for the given leafreader.
     *
     * The luceneField must be any existing Lucene field in the index.
     * It doesn't matter which. This is because BLTerms is used as an
     * intermediate to get access to BlackLab40StoredFieldsReader.
     *
     * The returned BlackLab40StoredFieldsReader is not specific for the specified field,
     * but can be used to read information related to any field from the segment.
     *
     * @param lrc leafreader to get the BLFieldsProducer for
     * @return BlackLab40StoredFieldsReader for this leafreader
     */
    public static BlackLab40StoredFieldsReader get(LeafReaderContext lrc) {
        try {
            if (fieldNameForCodecAccess == null)
                fieldNameForCodecAccess = BlackLab40Codec.findFieldNameForCodecAccess(lrc);
            return ((BLTerms)lrc.reader().terms(fieldNameForCodecAccess)).getStoredFieldsReader();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final FieldInfos fieldInfos;

    private final StoredFieldsReader delegate;

    public BlackLab40StoredFieldsReader(FieldInfos fieldInfos, StoredFieldsReader delegate) {
        this.fieldInfos = fieldInfos;
        this.delegate = delegate;
    }

    @Override
    public void visitDocument(int docId, StoredFieldVisitor storedFieldVisitor) throws IOException {
        for (FieldInfo fieldInfo: fieldInfos) {
            switch (storedFieldVisitor.needsField(fieldInfo)) {
            case YES:
                if (BlackLabIndexIntegrated.isContentStoreField(fieldInfo)) {
                    // This is a content store field.
                    visitContentStoreDocument(docId, fieldInfo, storedFieldVisitor);
                } else {
                    // This is a regular stored field. Delegate.
                    delegate.visitDocument(docId, storedFieldVisitor);
                }
            case NO:
                continue;
            case STOP:
                return;
            }
        }
    }

    /**
     * Retrieve contents from content store and pass them to visitor.
     *
     * @param docId              document id
     * @param storedFieldVisitor visitor that needs the contents
     */
    private void visitContentStoreDocument(int docId, FieldInfo fieldInfo, StoredFieldVisitor storedFieldVisitor)
            throws IOException {
        byte[] contents = getBytes(docId, fieldInfo);
        storedFieldVisitor.stringField(fieldInfo, contents);
    }

    /**
     * Get the field value as bytes.
     *
     * @param docId     document id
     * @param fieldInfo field to get
     * @return field value as bytes
     */
    private byte[] getBytes(int docId, FieldInfo fieldInfo) {
        // TODO: we might not need to get a String first, then decode it into bytes, but can get the
        //   bytes directly. The reason for going through String is that we use character offsets within
        //   the document, but that doesn't matter when getting the whole document.
        return getValue(docId, fieldInfo).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Get the entire field value.
     *
     * @param docId document id
     * @param fieldInfo field to get
     * @return field value
     */
    public String getValue(int docId, FieldInfo fieldInfo) {
        return getValueSubstring(docId, fieldInfo, 0, -1);
    }

    /**
     * Get part of the field value.
     *
     * @param docId document id
     * @param fieldInfo field to get
     * @param startChar first character to get. Must be zero or greater.
     * @param endChar character after the last character to get, or -1 for <code>value.length()</code>.
     * @return requested part
     */
    public String getValueSubstring(int docId, FieldInfo fieldInfo, int startChar, int endChar) {
        if (startChar < 0)
            throw new IllegalArgumentException("Illegal startChar value, must be >= 0: " + startChar);
        if (endChar < -1)
            throw new IllegalArgumentException("Illegal endChar value, must be >= -1: " + endChar);
        // TODO: implement
        return "TEST";
    }

    @Override
    public StoredFieldsReader clone() {
        return new BlackLab40StoredFieldsReader(fieldInfos, delegate.clone());
    }

    @Override
    public void checkIntegrity() throws IOException {
        delegate.checkIntegrity();

        // When is this called? Should we check our own files' checksum here as well?
    }

    @Override
    public void close() throws IOException {
        delegate.close();

        // TODO: close our Accountable's / other resources
    }

    @Override
    public long ramBytesUsed() {
        return delegate.ramBytesUsed();

        // TODO: use Lucene's RamUsageEstimator to estimate RAM usage
    }

    @Override
    public Collection<Accountable> getChildResources() {
        return delegate.getChildResources();

        // TODO: Add any Accountable's we hold a reference to
    }

    @Override
    public StoredFieldsReader getMergeInstance() {

        // For now we don't have a merging-optimized version of this class,
        // but maybe in the future.

        StoredFieldsReader mergeInstance = delegate.getMergeInstance();
        if (mergeInstance != delegate) {
            return new BlackLab40StoredFieldsReader(fieldInfos, mergeInstance);
        }
        return this;
    }

    @Override
    public String toString() {
        return "BlackLab40StoredFieldsReader(" + delegate.toString() + ")";
    }
}
