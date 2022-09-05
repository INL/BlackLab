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

    private final StoredFieldsFormat delegate;

    public BlackLab40StoredFieldsFormat(StoredFieldsFormat delegate) {
        this.delegate = delegate;
    }

    @Override
    public BlackLab40StoredFieldsReader fieldsReader(Directory directory, SegmentInfo segmentInfo,
            FieldInfos fieldInfos, IOContext ioContext) throws IOException {
        StoredFieldsReader delegateReader = delegate.fieldsReader(directory, segmentInfo, fieldInfos, ioContext);
        return new BlackLab40StoredFieldsReader(fieldInfos, delegateReader);
    }

    @Override
    public BlackLab40StoredFieldsWriter fieldsWriter(Directory directory, SegmentInfo segmentInfo, IOContext ioContext)
            throws IOException {
        StoredFieldsWriter delegateWriter = delegate.fieldsWriter(directory, segmentInfo, ioContext);
        return new BlackLab40StoredFieldsWriter(delegateWriter);
    }
}
