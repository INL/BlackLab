package nl.inl.blacklab.codec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.index.StoredFieldVisitor;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.Accountables;

import nl.inl.blacklab.contentstore.ContentStoreSegmentReader;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;

/**
 * Provides random access to values stored as a content store.
 * Delegates non-content-store reads to the default implementation.
 */
public class BlackLab40StoredFieldsReader extends StoredFieldsReader {

    /** Size in bytes of a record in the docindex file */
    private static final int DOCINDEX_RECORD_SIZE = Integer.BYTES + Byte.BYTES;

    /** Size in bytes of a record in the valueindex file */
    private static final int VALUEINDEX_RECORD_SIZE = Byte.BYTES + Integer.BYTES + 2 * Long.BYTES;

    /** Size in bytes of a record in the blockindex file */
    private static final int BLOCKINDEX_RECORD_SIZE = Integer.BYTES;

    /** Which field we use to access our Codec, so we can access the segment content store. */
    private static String fieldNameForCodecAccess;

    /** Our segment directory */
    private final Directory directory;

    /** Info about our segment */
    private final SegmentInfo segmentInfo;

    /** Lucene I/O context (?) */
    private final IOContext ioContext;

    /** Information about the fields in this segment */
    private final FieldInfos fieldInfos;

    /** Lucene stored fields reader we delegate to for non-content-store fields */
    private final StoredFieldsReader delegate;

    /** Fields with a content store and their field index. */
    private final Map<String, Integer> fields = new HashMap<>();

    /** Offset for each doc in the valueindex file, and number of fields stored */
    private final IndexInput _docIndexFile;

    /** Information about field, value length, codec, and offsets in the block* files */
    private final IndexInput _valueIndexFile;

    /** Offsets in the blocks file (1 or more for each value stored) */
    private final IndexInput _blockIndexFile;

    /** Encoded data blocks */
    private final IndexInput _blocksFile;

    /** Offset in docIndex file after header, so we can calculate doc offsets. */
    private final long docIndexFileOffset;

    /** How many characters from the document do we encode into a data block? */
    private final int blockSizeChars;

    /** Name of the StoredFieldsFormat we delegate to.
     *  We check the index files to make sure this matches. */
    private String delegateFormatName;

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

    public BlackLab40StoredFieldsReader(Directory directory, SegmentInfo segmentInfo, IOContext ioContext, FieldInfos fieldInfos,
            StoredFieldsReader delegate, String delegateFormatName)
            throws IOException {
        this.directory = directory;
        this.segmentInfo = segmentInfo;
        this.ioContext = ioContext;
        this.fieldInfos = fieldInfos;
        this.delegate = delegate;
        this.delegateFormatName = delegateFormatName; //delegateFormat.getClass().getSimpleName(); // check that this matches what was written

        IndexInput fieldsFile = openInput(BlackLab40StoredFieldsFormat.FIELDS_EXT, directory, segmentInfo, ioContext);
        blockSizeChars = fieldsFile.readInt();
        while (fieldsFile.getFilePointer() < fieldsFile.length()) {
            String fieldName = fieldsFile.readString();
            int id = fields.size();
            fields.put(fieldName, id);
        }
        fieldsFile.close();
        _docIndexFile = openInput(BlackLab40StoredFieldsFormat.DOCINDEX_EXT, directory, segmentInfo, ioContext);
        docIndexFileOffset = _docIndexFile.getFilePointer(); // remember offset after header so we can calculdate doc offsets.
        _valueIndexFile = openInput(BlackLab40StoredFieldsFormat.VALUEINDEX_EXT, directory, segmentInfo, ioContext);
        _blockIndexFile = openInput(BlackLab40StoredFieldsFormat.BLOCKINDEX_EXT, directory, segmentInfo, ioContext);
        _blocksFile = openInput(BlackLab40StoredFieldsFormat.BLOCKS_EXT, directory, segmentInfo, ioContext);
    }

    /**
     * Open a custom file for reading and check the header.
     *
     * Also detect the delegate format name, or check if it matches the one we already detected.
     *
     * @param extension   extension of the file to open (will automatically be prefixed with "blfi.")
     * @param directory   segment directory
     * @param segmentInfo segment info
     * @param ioContext   I/O context
     * @return handle to the opened segment file
     */
    private IndexInput openInput(String extension, Directory directory, SegmentInfo segmentInfo, IOContext ioContext) throws IOException {
        String segmentSuffix = "";
        String fileName = IndexFileNames.segmentFileName(segmentInfo.name, segmentSuffix, extension);
        IndexInput input = directory.openInput(fileName, ioContext);
        try {
            // Check index header
            String codecName = BlackLab40StoredFieldsFormat.NAME + "_" + extension;
            CodecUtil.checkIndexHeader(input, codecName, BlackLab40StoredFieldsFormat.VERSION_START,
                    BlackLab40StoredFieldsFormat.VERSION_CURRENT, segmentInfo.getId(), segmentSuffix);

            // Set or check delegate format name
            String delegateFN = input.readString();
            if (delegateFormatName == null)
                delegateFormatName = delegateFN;
            if (!delegateFormatName.equals(delegateFN))
                throw new IOException("Segment file " + fileName + " contains wrong delegate format name: " +
                        delegateFN + " (expected " + delegateFormatName + ")");
            return input;
        } catch (Exception e) {
            input.close();
            throw e;
        }
    }

    @Override
    public void visitDocument(int docId, StoredFieldVisitor storedFieldVisitor) throws IOException {
        // Visit each regular stored field.
        delegate.visitDocument(docId, storedFieldVisitor);

        // Visit each content store field.
        for (FieldInfo fieldInfo: fieldInfos) {
            switch (storedFieldVisitor.needsField(fieldInfo)) {
            case YES:
                // Visit this field.
                if (BlackLabIndexIntegrated.isContentStoreField(fieldInfo)) {
                    visitContentStoreDocument(docId, fieldInfo, storedFieldVisitor);
                }
                break;
            case NO:
                // No, we don't want to visit this field.
                break;
            case STOP:
                // We should stop visiting fields altogether.
                return;
            }
        }
    }

    /**
     * Retrieve contents from content store and pass them to visitor.
     *
     * This is used when retrieving the entire field value from Lucene, and when segments are merged.
     *
     * @param docId              document id
     * @param storedFieldVisitor visitor that needs the contents
     */
    private void visitContentStoreDocument(int docId, FieldInfo fieldInfo, StoredFieldVisitor storedFieldVisitor)
            throws IOException {
        byte[] contents = contentStore().getBytes(docId, fieldInfo.name);
        storedFieldVisitor.stringField(fieldInfo, contents);
    }

    /**
     * Get the index (in the fields file) of a field we're writing.
     *
     * If the field did not have an index yet, write it to the fields file and
     * assign the index.
     *
     * @param luceneField field to get index for
     * @return index for the field
     */
    private int getFieldIndex(String luceneField) {
        return fields.get(luceneField);
    }

    @Override
    public StoredFieldsReader clone() {
        try {
            return new BlackLab40StoredFieldsReader(directory, segmentInfo, ioContext, fieldInfos,
                    delegate.clone(), delegateFormatName);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void checkIntegrity() throws IOException {
        delegate.checkIntegrity();

        // When is this called? Should we check our own files' checksum here as well?
    }

    @Override
    public void close() throws IOException {
        // Close our files
        _docIndexFile.close();
        _valueIndexFile.close();
        _blockIndexFile.close();
        _blocksFile.close();

        // Let the delegate close its files.
        delegate.close();
    }

    @Override
    public long ramBytesUsed() {
        return delegate.ramBytesUsed() /* +
                RamUsageEstimator.sizeOfObject(fieldsFile) +
                RamUsageEstimator.sizeOfObject(docIndexFile) +
                RamUsageEstimator.sizeOfObject(valueIndexFile) +
                RamUsageEstimator.sizeOfObject(blockIndexFile) +
                RamUsageEstimator.sizeOfObject(blocksFile) +
                Integer.BYTES * 2 + // blockSizeChars, numberOfFieldsWritten
                RamUsageEstimator.sizeOfMap(fields)
                */ ;
    }

    @Override
    public Collection<Accountable> getChildResources() {
        return List.of(Accountables.namedAccountable("delegate", delegate));
    }

    @Override
    public StoredFieldsReader getMergeInstance() {

        // For now we don't have a merging-optimized version of this class,
        // but maybe in the future.

        StoredFieldsReader mergeInstance = delegate.getMergeInstance();
        if (mergeInstance != delegate) {
            try {
                return new BlackLab40StoredFieldsReader(directory, segmentInfo, ioContext, fieldInfos,
                        mergeInstance, delegateFormatName);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return this;
    }

    @Override
    public String toString() {
        return "BlackLab40StoredFieldsReader(" + delegate.toString() + ")";
    }

    /**
     * Create a content store reader for this segment.
     *
     * The returned reader is not threadsafe and shouldn't be stored.
     * A single thread may use it for reading from this segment. It
     * can then be discarded.
     *
     * @return content store segment reader
     */
    public ContentStoreSegmentReader contentStore() {
        return new ContentStoreSegmentReader() {

            // Clones of the various file handles, so we can reposition them without
            // causing problems. Cloned IndexInputs don't need to be closed.
            private final IndexInput docIndexFile = _docIndexFile.clone();
            private final IndexInput valueIndexFile = _valueIndexFile.clone();
            private final IndexInput blockIndexFile = _blockIndexFile.clone();
            private final IndexInput blocksFile = _blocksFile.clone();

            /**
             * Get the field value as bytes.
             *
             * @param docId     document id
             * @param luceneField field to get
             * @return field value as bytes
             */
            public byte[] getBytes(int docId, String luceneField) {
                // TODO: we might not need to get a String first, then decode it into bytes, but can get the
                //   bytes directly. The reason for going through String is that we use character offsets within
                //   the document, but that doesn't matter when getting the whole document.
                return getValue(docId, luceneField).getBytes(StandardCharsets.UTF_8);
            }

            /**
             * Get the entire field value.
             *
             * @param docId document id
             * @param luceneField field to get
             * @return field value
             */
            public String getValue(int docId, String luceneField) {
                return getValueSubstring(docId, luceneField, 0, -1);
            }

            /**
             * Get part of the field value.
             *
             * @param docId document id
             * @param luceneField field to get
             * @param startChar first character to get. Must be zero or greater.
             * @param endChar character after the last character to get, or -1 for <code>value.length()</code>.
             * @return requested part
             */
            public String getValueSubstring(int docId, String luceneField, int startChar, int endChar) {
                if (startChar < 0)
                    throw new IllegalArgumentException("Illegal startChar value, must be >= 0: " + startChar);
                if (endChar < -1)
                    throw new IllegalArgumentException("Illegal endChar value, must be >= -1: " + endChar);
                if (endChar != -1 && startChar > endChar)
                    throw new IllegalArgumentException("Illegal startChar/endChar values, startChar > endChar: " +
                            startChar + "-" + endChar);

                try {
                    // Find the value length in characters, and position the valueIndex file pointer
                    // to read the rest of the information we need: where to find the block indexes
                    // and where the blocks start.
                    int valueLengthChar = findValueLengthChar(docId, luceneField);
                    if (valueLengthChar == 0)
                        return ""; // no value stored for this document
                    if (startChar > valueLengthChar)
                        startChar = valueLengthChar;
                    if (endChar == -1 || endChar > valueLengthChar)
                        endChar = valueLengthChar;
                    ContentStoreBlockCodec blockCodec = ContentStoreBlockCodec.fromCode(valueIndexFile.readByte());
                    long blockIndexOffset = valueIndexFile.readLong();
                    long blocksOffset = valueIndexFile.readLong();

                    // Determine what blocks we'll need
                    int firstBlockNeeded = startChar / blockSizeChars;
                    int lastBlockNeeded = endChar / blockSizeChars;
                    int numBlocksNeeded = lastBlockNeeded - firstBlockNeeded + 1;

                    // Determine where our first block starts, and position blockindex file
                    // to start reading subsequent after-block positions
                    int blockStartOffset;
                    if (firstBlockNeeded == 0) {
                        // Start of the first block is always 0. We don't store that.
                        blockStartOffset = 0;
                        blockIndexFile.seek(blockIndexOffset);
                        blocksFile.seek(blocksOffset);
                    } else {
                        // Start of block n is end of block n-1; read it from the block index file.
                        blockIndexFile.seek(blockIndexOffset + (long) (firstBlockNeeded - 1) * BLOCKINDEX_RECORD_SIZE);
                        blockStartOffset = blockIndexFile.readInt();
                        blocksFile.seek(blocksOffset + blockStartOffset);
                    }

                    int currentBlockCharOffset = firstBlockNeeded * blockSizeChars;
                    int blocksRead = 0;
                    StringBuilder result = new StringBuilder();
                    try (ContentStoreBlockCodec.Decoder decoder = blockCodec.getDecoder()) {
                        while (blocksRead < numBlocksNeeded) {

                            // Read a block and decompress it.
                            int blockEndOffset = blockIndexFile.readInt();
                            int blockSizeBytes = blockEndOffset - blockStartOffset;
                            byte[] block = new byte[blockSizeBytes];
                            blocksFile.readBytes(block, 0, blockSizeBytes);
                            String blockDecompressed = decoder.decode(block, 0, blockSizeBytes);

                            // Append the content we need to the result.
                            if (blocksRead == 0) {
                                // First block. Only take the part we need.
                                if (numBlocksNeeded == 1) {
                                    // This is the only block we need. Take the requested part from the middle.
                                    result.append(blockDecompressed, startChar - currentBlockCharOffset,
                                            endChar - currentBlockCharOffset);
                                } else {
                                    // We'll need more blocks. Take part at the end.
                                    result.append(blockDecompressed, startChar - currentBlockCharOffset,
                                            blockSizeChars);
                                }
                            } else if (blocksRead == numBlocksNeeded - 1) {
                                // Last block. Take the part we need from the beginning.
                                result.append(blockDecompressed, 0, endChar - currentBlockCharOffset);
                            } else {
                                // Middle block. Append the whole thing.
                                result.append(blockDecompressed);
                            }

                            // Update variables to read the next block
                            blockStartOffset = blockEndOffset;
                            currentBlockCharOffset += blockSizeChars;
                            blocksRead++;
                        }
                    }
                    return result.toString();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            /**
             * Finds the length in characters of a stored value.
             *
             * Also positions the valueIndexFile pointer to just after the doc length,
             * from which we can continue reading information about the value (such as where
             * to find the actual value itself).
             *
             * @param docId document id
             * @param luceneField field to get length for
             * @return length of the value in characters
             */
            private int findValueLengthChar(int docId, String luceneField) throws IOException {
                // What's the id of the field that is references in the value index file?
                int fieldId = getFieldIndex(luceneField);

                // Find the document
                docIndexFile.seek(docIndexFileOffset + (long) docId * DOCINDEX_RECORD_SIZE);
                int valueIndexOffset = docIndexFile.readInt();
                byte numberOfContentStoreFields = docIndexFile.readByte();

                // Find the correct field in this document
                int i = 0;
                while (i < numberOfContentStoreFields) {
                    valueIndexFile.seek(valueIndexOffset + (long) i * VALUEINDEX_RECORD_SIZE);
                    byte thisFieldId = valueIndexFile.readByte();
                    if (thisFieldId == fieldId)
                        break;
                    i++;
                }
                if (i == numberOfContentStoreFields)
                    return 0;
                //    throw new IllegalStateException("CS field " + fieldId + " (" + fieldInfo.name +
                //            ") not found for docId " + docId);

                // Read document length, where to find the block indexes and where the blocks start.
                return valueIndexFile.readInt();
            }

            /**
             * Get the length of a value in the content store.
             *
             * @param docId document id
             * @param luceneField field to get length for
             * @return length of the value in characters
             */
            public int valueLength(int docId, String luceneField) {
                try {
                    return findValueLengthChar(docId, luceneField);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            /**
             * Get several parts of the field value.
             *
             * @param docId document id
             * @param luceneField field to get
             * @param start positions of the first character to get. Must all be zero or greater.
             * @param end positions of the character after the last character to get, or -1 for <code>value.length()</code>.
             * @return requested parts
             */
            public String[] getValueSubstrings(int docId, String luceneField, int[] start, int[] end) {
                if (start.length != end.length)
                    throw new IllegalArgumentException("Different numbers of starts and ends provided: " + start.length + ", " + end.length);
                // TODO: we could optimize this to avoid reading blocks twice!
                //   easiest is to determine the lowest start and highest end, read the entire document part,
                //   then cut the snippets from that. More efficient is to figure out exactly which blocks we
                //   need, retrieve those, then cut the snippets.
                String[] results = new String[start.length];
                for (int i = 0; i < start.length; i++) {
                    results[i] = getValueSubstring(docId, luceneField, start[i], end[i]);
                }
                return results;
            }
        };
    }
}
