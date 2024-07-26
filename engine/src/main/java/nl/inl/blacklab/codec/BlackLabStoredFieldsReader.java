package nl.inl.blacklab.codec;

import org.apache.lucene.codecs.StoredFieldsReader;

import nl.inl.blacklab.contentstore.ContentStoreSegmentReader;

public abstract class BlackLabStoredFieldsReader extends StoredFieldsReader {
    public abstract ContentStoreSegmentReader contentStore();
}
