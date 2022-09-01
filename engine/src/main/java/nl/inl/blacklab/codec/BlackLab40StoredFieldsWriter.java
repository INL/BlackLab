package nl.inl.blacklab.codec;

import java.io.IOException;

import org.apache.lucene.codecs.StoredFieldsWriter;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexableField;

/**
 * Stores values as a content store, to enable random access.
 *
 * Delegates non-content-store writes to the default implementation.
 */
public class BlackLab40StoredFieldsWriter extends StoredFieldsWriter {

    private final StoredFieldsWriter delegate;

    public BlackLab40StoredFieldsWriter(StoredFieldsWriter delegate) {
        this.delegate = delegate;
    }

    @Override public void startDocument() throws IOException {
        delegate.startDocument();
    }

    @Override public void writeField(FieldInfo fieldInfo, IndexableField indexableField) throws IOException {
        delegate.writeField(fieldInfo, indexableField);
    }

    @Override public void finish(FieldInfos fieldInfos, int i) throws IOException {
        delegate.finish(fieldInfos, i);
    }

    @Override public void close() throws IOException {
        delegate.close();
    }

    @Override public long ramBytesUsed() {
        return delegate.ramBytesUsed();
    }
}
