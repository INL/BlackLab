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

    /** Name of this codec. Written to the files and checked on reading. */
    public static final String NAME = "BlackLab40ContentStore";

    /** Oldest version still supported */
    public static final int VERSION_START = 1;

    /** Current version */
    public static final int VERSION_CURRENT = 1;

    /** Every file extension will be prefixed with this to indicate it is part of the content store. */
    public static final String EXT_PREFIX = "blcs.";

    /** Extension for the fields file, that stores block size and Lucene fields with a CS. */
    public static final String FIELDS_EXT = EXT_PREFIX + "fields";

    /** Extension for the docindex file. */
    public static final String DOCINDEX_EXT = EXT_PREFIX + "docindex";

    /** Extension for the valueindex file. */
    public static final String VALUEINDEX_EXT = EXT_PREFIX + "valueindex";

    /** Extension for the blockindex file. */
    public static final String BLOCKINDEX_EXT = EXT_PREFIX + "blockindex";

    /** Extension for the blocks file. */
    public static final String BLOCKS_EXT = EXT_PREFIX + "blocks";

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
        String delegateFormatName = delegate.getClass().getSimpleName();
        return new BlackLab40StoredFieldsReader(directory, segmentInfo, ioContext, fieldInfos, delegateReader,
                delegateFormatName);
    }

    @Override
    public BlackLab40StoredFieldsWriter fieldsWriter(Directory directory, SegmentInfo segmentInfo, IOContext ioContext)
            throws IOException {
        StoredFieldsWriter delegateWriter = delegate.fieldsWriter(directory, segmentInfo, ioContext);
        String delegateFormatName = delegate.getClass().getSimpleName();
        return new BlackLab40StoredFieldsWriter(directory, segmentInfo, ioContext, delegateWriter, delegateFormatName);
    }
}
