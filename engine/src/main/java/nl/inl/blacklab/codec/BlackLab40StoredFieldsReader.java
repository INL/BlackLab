package nl.inl.blacklab.codec;

import java.io.IOException;

import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.index.StoredFieldVisitor;

/**
 * Provides random access to values stored as a content store.
 *
 * Delegates non-content-store reads to the default implementation.
 */
public class BlackLab40StoredFieldsReader extends StoredFieldsReader {

    private final StoredFieldsReader delegate;

    public BlackLab40StoredFieldsReader(StoredFieldsReader delegate) {
        this.delegate = delegate;
    }

    @Override public void visitDocument(int i, StoredFieldVisitor storedFieldVisitor) throws IOException {
        delegate.visitDocument(i, storedFieldVisitor);
    }

    @Override public StoredFieldsReader clone() {
        return new BlackLab40StoredFieldsReader(delegate.clone());
    }

    @Override public void checkIntegrity() throws IOException {
        delegate.checkIntegrity();
    }

    @Override public void close() throws IOException {
        delegate.close();;
    }

    @Override public long ramBytesUsed() {
        return delegate.ramBytesUsed();
    }
}
