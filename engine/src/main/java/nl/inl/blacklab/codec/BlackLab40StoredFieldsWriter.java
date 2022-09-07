package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.StoredFieldsWriter;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MergeState;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.RamUsageEstimator;

import nl.inl.blacklab.search.BlackLabIndexIntegrated;

/**
 * Stores values as a content store, to enable random access.
 * Delegates non-content-store writes to the default implementation.
 */
public class BlackLab40StoredFieldsWriter extends StoredFieldsWriter {

    private final IndexOutput fieldsFile;

    private final IndexOutput docIndexFile;

    private final IndexOutput valueIndexFile;

    private final IndexOutput blockIndexFile;

    private final IndexOutput blocksFile;

    /** Fields with a content store and their field index. */
    private final Map<String, Integer> fields = new HashMap<>();

    /** How we (de)compress our blocks. */
    private final ContentStoreBlockCodec blockCodec = ContentStoreBlockCodecZlib.INSTANCE;

    /** Lucene's default stored fields writer, for regular stored fields. */
    private final StoredFieldsWriter delegate;

    /** Class name for the delegate StoredFieldsFormat, which we will write to our file headers,
     * so we can ensure we're using the right delegate format when reading.
     */
    private final String delegateFormatName;

    /** How many characters per compressed block. */
    private final int blockSizeChars = BlackLab40StoredFieldsFormat.DEFAULT_BLOCK_SIZE_CHARS;

    /** How many CS fields were written for the current document? */
    private byte numberOfFieldsWritten;

    public BlackLab40StoredFieldsWriter(Directory directory, SegmentInfo segmentInfo, IOContext ioContext,
            StoredFieldsWriter delegate, String delegateFormatName)
            throws IOException {
        this.delegate = delegate;
        this.delegateFormatName = delegateFormatName;

        fieldsFile = createOutput(BlackLab40StoredFieldsFormat.FIELDS_EXT, directory, segmentInfo, ioContext);

        // NOTE: we can make this configurable later (to optimize for specific usage scenarios),
        // but for now we'll just use the default value.
        fieldsFile.writeInt(blockSizeChars);

        docIndexFile = createOutput(BlackLab40StoredFieldsFormat.DOCINDEX_EXT, directory, segmentInfo, ioContext);
        valueIndexFile = createOutput(BlackLab40StoredFieldsFormat.VALUEINDEX_EXT, directory, segmentInfo, ioContext);
        blockIndexFile = createOutput(BlackLab40StoredFieldsFormat.BLOCKINDEX_EXT, directory, segmentInfo, ioContext);
        blocksFile = createOutput(BlackLab40StoredFieldsFormat.BLOCKS_EXT, directory, segmentInfo, ioContext);
    }

    private IndexOutput createOutput(String ext, Directory directory, SegmentInfo segmentInfo, IOContext ioContext)
            throws IOException {
        final IndexOutput indexOutput;
        String codecName = BlackLab40StoredFieldsFormat.NAME + "_" + ext;
        String segmentSuffix = "";
        indexOutput = directory.createOutput(IndexFileNames.segmentFileName(segmentInfo.name, segmentSuffix, ext),
                ioContext);
        CodecUtil.writeIndexHeader(indexOutput, codecName, BlackLab40StoredFieldsFormat.VERSION_CURRENT,
                segmentInfo.getId(), segmentSuffix);
        assert CodecUtil.indexHeaderLength(codecName, segmentSuffix) == indexOutput.getFilePointer();
        indexOutput.writeString(delegateFormatName);
        return indexOutput;
    }

    @Override
    public void startDocument() throws IOException {
        docIndexFile.writeInt((int)valueIndexFile.getFilePointer());
        numberOfFieldsWritten = 0; // we'll record this at the end of the doc

        delegate.startDocument();
    }

    /**
     * Write a single stored field or content store value.
     *
     * Will either delegate to the default stored field writer,
     * or will store the value in the content store.
     *
     * @param fieldInfo field to write
     * @param indexableField value to write
     */
    @Override
    public void writeField(FieldInfo fieldInfo, IndexableField indexableField) throws IOException {
        if (BlackLabIndexIntegrated.isContentStoreField(fieldInfo)) {
            // This is a content store field.
            writeContentStoreField(fieldInfo, indexableField.stringValue());
        } else {
            // This is a regular stored field. Delegate.
            delegate.writeField(fieldInfo, indexableField);
        }
    }

    /**
     * Write a content store field to the content store files.
     *
     * @param fieldInfo field to write
     * @param value string value for the field
     */
    private void writeContentStoreField(FieldInfo fieldInfo, String value) throws IOException {
        // Write some info about this value
        valueIndexFile.writeByte(getFieldIndex(fieldInfo)); // which field is this?
        int lengthChars = value.length();
        valueIndexFile.writeInt(lengthChars);
        valueIndexFile.writeByte(blockCodec.getCode());
        valueIndexFile.writeLong(blockIndexFile.getFilePointer());
        long baseOffset = blocksFile.getFilePointer();
        valueIndexFile.writeLong(baseOffset);

        // Write blocks and block offsets
        int numberOfBlocks = (lengthChars + blockSizeChars - 1) / blockSizeChars; // ceil(lengthInChars/blockSizeChars)
        ContentStoreBlockCodec.Encoder encoder = blockCodec.createEncoder();
        byte[] buffer = new byte[blockSizeChars * 2]; // should always be plenty of room
        for (int i = 0; i < numberOfBlocks; i++) {

            int blockOffset = i * blockSizeChars;
            int blockLength = Math.min(blockSizeChars, value.length() - blockOffset);
            //String block = value.substring(blockOffset, blockOffset + blockLength);

            // Compress block and write to values file
            int bytesWritten = encoder.encode(value, blockOffset, blockLength, buffer, 0, buffer.length);
            if (bytesWritten >= buffer.length) {
                throw new IOException("Insufficient buffer space for encoding block");
            }

            blocksFile.writeBytes(buffer, 0, bytesWritten);

            // Write offset after block to index file
            int offset = (int)(blocksFile.getFilePointer() - baseOffset);
            blockIndexFile.writeInt(offset);
        }

        // Keep track of the number of values written for this doc, so we can record that later.
        numberOfFieldsWritten++;
        if (numberOfFieldsWritten == 128) {
            throw new IllegalStateException("Too many content store fields for document (>127)");
        }
    }

    /**
     * Get the index (in the fields file) of a field we're writing.
     *
     * If the field did not have an index yet, write it to the fields file and
     * assign the index.
     *
     * @param fieldInfo field to get index for
     * @return index for the field
     */
    private byte getFieldIndex(FieldInfo fieldInfo) throws IOException {
        String name = fieldInfo.name;
        Integer id = fields.get(name);
        if (id == null) {
            id = fields.size();
            if (id == 128)
                throw new IllegalStateException("Too many content store fields (>127)");
            fields.put(name, id);
            fieldsFile.writeString(name);
        }
        return (byte)(int)id;
    }

    @Override
    public void finish(FieldInfos fieldInfos, int i) throws IOException {
        delegate.finish(fieldInfos, i);
    }

    @Override
    public void close() throws IOException {
        // Close our files
        fieldsFile.close();
        docIndexFile.close();
        valueIndexFile.close();
        blockIndexFile.close();
        blocksFile.close();

        // Let the delegate close its files
        delegate.close();
    }

    @Override
    public long ramBytesUsed() {
        return delegate.ramBytesUsed() +
                RamUsageEstimator.sizeOfObject(fieldsFile) +
                RamUsageEstimator.sizeOfObject(docIndexFile) +
                RamUsageEstimator.sizeOfObject(valueIndexFile) +
                RamUsageEstimator.sizeOfObject(blockIndexFile) +
                RamUsageEstimator.sizeOfObject(blocksFile) +
                Integer.BYTES * 2 + // blockSizeChars, numberOfFieldsWritten
                RamUsageEstimator.sizeOfMap(fields);
    }

    @Override
    public void finishDocument() throws IOException {
        // Record how many fields were written for this doc (could be 0)
        docIndexFile.writeByte(numberOfFieldsWritten);

        delegate.finishDocument();
    }

    @Override
    public int merge(MergeState mergeState) throws IOException {

        // TODO: the default implementation just reads all the fields for each document
        //   and writes them again. This works but is relatively slow.
        //   More efficient is to copy most information (esp. compressed blocks)
        //   directly from the readers to the new contentstore files.
        return super.merge(mergeState);

        // Don't call the delegate here because the default implementation processes all the fields,
        // both the regular stored fields and the content store fields.
        //return delegate.merge(mergeState);
    }

    @Override
    public Collection<Accountable> getChildResources() {
        // TODO: add any Accountables we hold (none?)

        return delegate.getChildResources();
    }

    @Override
    public String toString() {
        return "BlackLab40StoredFieldsWriter(" + delegate.getClass().getName() + ")";
    }

}
