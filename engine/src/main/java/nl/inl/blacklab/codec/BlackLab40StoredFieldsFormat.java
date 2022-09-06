package nl.inl.blacklab.codec;

import java.io.IOException;

import org.apache.lucene.codecs.StoredFieldsFormat;
import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.codecs.StoredFieldsWriter;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;

/**
 * Stores certain fields as a content store, enabling random access to the stored values.
 *
 * Delegates non-content-store writes and reads to the default implementation.
 */
public class BlackLab40StoredFieldsFormat extends StoredFieldsFormat {

    public static final String NAME = "BlackLab40ContentStore";

    public static final int VERSION_START = 1;

    public static final int VERSION_CURRENT = 1;

    /** Every file extension will be prefixed with this to indicate it is part of the forward index. */
    public static final String CONTENT_STORE_EXT_PREFIX = "blcs.";

    /** Extension for the fields file, that stores block size and Lucene fields with a CS. */
    public static final String FIELDS_EXT = "fields";

    /** Extension for the docindex file. */
    public static final String DOCINDEX_EXT = "docindex";

    public static final String VALUEINDEX_EXT = "valueindex";

    public static final String BLOCKINDEX_EXT = "blockindex";

    public static final String BLOCKS_EXT = "blocks";

    /** Default compressed block size (in characters) in the values file */
    public static final int DEFAULT_BLOCK_SIZE_CHARS = 4096;

    /** Standard Lucene StoredFieldsFormat we delegate to for regular (non-content-store) stored fields. */
    private final StoredFieldsFormat delegate;

    public BlackLab40StoredFieldsFormat(StoredFieldsFormat delegate) {
        this.delegate = delegate;
    }

    @Override
    public BlackLab40StoredFieldsReader fieldsReader(Directory directory, SegmentInfo segmentInfo,
            FieldInfos fieldInfos, IOContext ioContext) throws IOException {
        StoredFieldsReader delegateReader = delegate.fieldsReader(directory, segmentInfo, fieldInfos, ioContext);
        return new BlackLab40StoredFieldsReader(directory, segmentInfo, ioContext, fieldInfos, delegateReader);
    }

    @Override
    public BlackLab40StoredFieldsWriter fieldsWriter(Directory directory, SegmentInfo segmentInfo, IOContext ioContext)
            throws IOException {
        StoredFieldsWriter delegateWriter = delegate.fieldsWriter(directory, segmentInfo, ioContext);
        return new BlackLab40StoredFieldsWriter(directory, segmentInfo, ioContext, delegateWriter);
    }
}
