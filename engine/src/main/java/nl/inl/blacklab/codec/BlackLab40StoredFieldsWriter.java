package nl.inl.blacklab.codec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
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

    private final Map<String, Integer> fields = new HashMap<>();

    private final StoredFieldsWriter delegate;

    private final int blockSize = BlackLab40StoredFieldsFormat.DEFAULT_BLOCK_SIZE_CHARS;

    /** How many CS fields were written for the current document? */
    private int numberOfFieldsWritten;

    public BlackLab40StoredFieldsWriter(Directory directory, SegmentInfo segmentInfo, IOContext ioContext, StoredFieldsWriter delegate)
            throws IOException {
        this.delegate = delegate;

        fieldsFile = createOutput(BlackLab40StoredFieldsFormat.FIELDS_EXT, directory, segmentInfo, ioContext);

        // NOTE: we can make this configurable later (to optimize for specific usage scenarios),
        // but for now we'll just use the default value.
        fieldsFile.writeInt(blockSize);

        docIndexFile = createOutput(BlackLab40StoredFieldsFormat.DOCINDEX_EXT, directory, segmentInfo, ioContext);
        valueIndexFile = createOutput(BlackLab40StoredFieldsFormat.VALUEINDEX_EXT, directory, segmentInfo, ioContext);
        blockIndexFile = createOutput(BlackLab40StoredFieldsFormat.BLOCKINDEX_EXT, directory, segmentInfo, ioContext);
        blocksFile = createOutput(BlackLab40StoredFieldsFormat.BLOCKS_EXT, directory, segmentInfo, ioContext);
    }

    private IndexOutput createOutput(String ext, Directory directory, SegmentInfo segmentInfo, IOContext ioContext)
            throws IOException {
        final IndexOutput fieldsFile;
        ext = BlackLab40StoredFieldsFormat.CONTENT_STORE_EXT_PREFIX + ext;
        String codecName = BlackLab40StoredFieldsFormat.NAME + "_" + ext;
        String segmentSuffix = "";
        fieldsFile = directory.createOutput(IndexFileNames.segmentFileName(segmentInfo.name, segmentSuffix, ext),
                ioContext);
        CodecUtil.writeIndexHeader(fieldsFile, codecName, BlackLab40StoredFieldsFormat.VERSION_CURRENT,
                segmentInfo.getId(), segmentSuffix);
        assert CodecUtil.indexHeaderLength(codecName, segmentSuffix) == fieldsFile.getFilePointer();
        return fieldsFile;
    }

    @Override
    public void startDocument() throws IOException {
        docIndexFile.writeInt((int)valueIndexFile.getFilePointer());
        numberOfFieldsWritten = 0; // we'll record this at the end of the doc

        delegate.startDocument();
    }

    @Override
    public void writeField(FieldInfo fieldInfo, IndexableField indexableField) throws IOException {
        if (isContentStore(fieldInfo)) {
            // This is a content store field.
            writeContentStoreField(fieldInfo, indexableField);
        } else {
            // This is a regular stored field. Delegate.
            delegate.writeField(fieldInfo, indexableField);
        }
    }

    private boolean isContentStore(FieldInfo fieldInfo) {
        return fieldInfo.getAttribute(BlackLabIndexIntegrated.BLFA_CONTENT_STORE).equals("true");
    }

    private void writeContentStoreField(FieldInfo fieldInfo, IndexableField indexableField) throws IOException {
        // Write some info about this value
        valueIndexFile.writeInt(getFieldIndex(fieldInfo)); // which field is this?
        String value = indexableField.stringValue();
        int lengthChars = value.length();
        valueIndexFile.writeInt(lengthChars);
        valueIndexFile.writeLong(blockIndexFile.getFilePointer());
        long baseOffset = blocksFile.getFilePointer();
        valueIndexFile.writeLong(baseOffset);

        // Write blocks and block offsets
        int numberOfBlocks = (lengthChars + blockSize - 1) / blockSize; // Math.ceil(lengthInChars / blockSize)
        for (int i = 0; i < numberOfBlocks; i++) {

            int blockOffset = i * blockSize;
            String block = value.substring(blockOffset, blockOffset + blockSize);

            // Compress block and write to values file
            byte[] compressedBlock = block.getBytes(StandardCharsets.UTF_8); // @@@ TODO: actually compress this!

            blocksFile.writeBytes(compressedBlock, 0, compressedBlock.length);

            // Write offset after block to index file
            int offset = (int)(blocksFile.getFilePointer() - baseOffset);
            blockIndexFile.writeInt(offset);
        }

        // Keep track of the number of values written for this doc, so we can record that later.
        numberOfFieldsWritten++;
    }

    private int getFieldIndex(FieldInfo fieldInfo) throws IOException {
        String name = fieldInfo.name;
        Integer id = fields.get(name);
        if (id == null) {
            id = fields.size();
            fields.put(name, id);
            fieldsFile.writeString(name);
        }
        return id;
    }

    @Override
    public void finish(FieldInfos fieldInfos, int i) throws IOException {
        delegate.finish(fieldInfos, i);
    }

    @Override
    public void close() throws IOException {
        // Close all our files
        fieldsFile.close();
        docIndexFile.close();
        valueIndexFile.close();;
        blockIndexFile.close();
        blocksFile.close();

        // Let the delegate close its files
        delegate.close();
    }

    @Override
    public long ramBytesUsed() {
        return delegate.ramBytesUsed();

        // @@@ TODO: use Lucene's RamUsageEstimator to estimate RAM usage
    }

    @Override
    public void finishDocument() throws IOException {
        // Record how many fields were written for this doc (could be 0)
        docIndexFile.writeInt(numberOfFieldsWritten);

        delegate.finishDocument();
    }

    @Override
    public int merge(MergeState mergeState) throws IOException {

        // TODO: merge our stuff as well!

        return delegate.merge(mergeState);
    }

    @Override
    public Collection<Accountable> getChildResources() {
        return delegate.getChildResources();
    }

    @Override
    public String toString() {
        return "BlackLab40StoredFieldsWriter(" + delegate.getClass().getName() + ")";
    }

}
