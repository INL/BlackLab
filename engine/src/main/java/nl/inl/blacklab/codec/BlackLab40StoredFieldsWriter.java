package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.Collection;

import org.apache.lucene.codecs.StoredFieldsWriter;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.MergeState;
import org.apache.lucene.util.Accountable;

/**
 * Stores values as a content store, to enable random access.
 * Delegates non-content-store writes to the default implementation.
 */
public class BlackLab40StoredFieldsWriter extends StoredFieldsWriter {

    private final StoredFieldsWriter delegate;

    public BlackLab40StoredFieldsWriter(StoredFieldsWriter delegate) {
        this.delegate = delegate;
    }

    @Override
    public void startDocument() throws IOException {
        delegate.startDocument();
    }

    @Override
    public void writeField(FieldInfo fieldInfo, IndexableField indexableField) throws IOException {
        delegate.writeField(fieldInfo, indexableField);
    }

    @Override
    public void finish(FieldInfos fieldInfos, int i) throws IOException {
        delegate.finish(fieldInfos, i);
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }

    @Override
    public long ramBytesUsed() {
        return delegate.ramBytesUsed();

        // @@@ TODO: use Lucene's RamUsageEstimator to estimate RAM usage
    }

    @Override
    public void finishDocument() throws IOException {
        delegate.finishDocument();
    }

    @Override
    public int merge(MergeState mergeState) throws IOException {
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
