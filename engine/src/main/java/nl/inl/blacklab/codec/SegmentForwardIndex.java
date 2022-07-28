package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.store.IndexInput;

import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.forwardindex.ForwardIndexAbstract;
import nl.inl.blacklab.forwardindex.ForwardIndexSegmentReader;
import nl.inl.blacklab.forwardindex.TermsSegmentReader;
import nl.inl.blacklab.search.BlackLabIndexAbstract;

/**
 * Managers read access to forward indexes for a single segment.
 */
@ThreadSafe
class SegmentForwardIndex implements AutoCloseable {

    /** Information about Lucene fields that represent BlackLab annotations */
    private static class Fields {
        Map<String, Field> fieldsByName = new LinkedHashMap<>();

        public Fields(IndexInput input) throws IOException {
            long size = input.length();
            while (input.getFilePointer() < size) {
                String fieldName = input.readString();
                long termIndexOffset = input.readLong();
                long tokensIndexOffset = input.readLong();
                Field field = new Field(fieldName, termIndexOffset, tokensIndexOffset);
                fieldsByName.put(fieldName, field);
            }
        }

        public Field get(String name) {
            return fieldsByName.get(name);
        }
    }

    /** Information about a Lucene field that represents a BlackLab annotation */
    private static class Field {

        private final String fieldName;

        private final long termIndexOffset;

        private final long tokensIndexOffset;

        public Field(String fieldName, long termIndexOffset, long tokensIndexOffset) {
            this.fieldName = fieldName;
            this.termIndexOffset = termIndexOffset;
            this.tokensIndexOffset = tokensIndexOffset;
        }

        public String getFieldName() {
            return fieldName;
        }

        public long getTermIndexOffset() {
            return termIndexOffset;
        }

        public long getTokensIndexOffset() {
            return tokensIndexOffset;
        }
    }

    /** Our fields producer */
    private final BLFieldsProducer fieldsProducer;

    /** Contains field names and offsets to term index file, where the terms for the field can be found */
    private final Fields fields;

    /** Contains offsets into termsFile where the string for each term can be found */
    private IndexInput _termIndexFile;

    /** Contains term strings for all fields */
    private IndexInput _termsFile;

    /** Contains indexes into the tokens file for all field and documents */
    private IndexInput _tokensIndexFile;

    /** Contains the tokens for all fields and documents */
    private IndexInput _tokensFile;

    public SegmentForwardIndex(BLFieldsProducer fieldsProducer, SegmentReadState state) throws IOException {
        this.fieldsProducer = fieldsProducer;

        try (IndexInput fieldsFile = fieldsProducer.openIndexFile(state, BLCodecPostingsFormat.FIELDS_EXT)) {
            fields = new Fields(fieldsFile);
        }

        _termIndexFile = fieldsProducer.openIndexFile(state, BLCodecPostingsFormat.TERMINDEX_EXT);
        _termsFile = fieldsProducer.openIndexFile(state, BLCodecPostingsFormat.TERMS_EXT);
        _tokensIndexFile = fieldsProducer.openIndexFile(state, BLCodecPostingsFormat.TOKENS_INDEX_EXT);
        _tokensFile = fieldsProducer.openIndexFile(state, BLCodecPostingsFormat.TOKENS_EXT);
    }

    @Override
    public void close() {
        try {
            _tokensFile.close();
            _tokensIndexFile.close();
            _termsFile.close();
            _termIndexFile.close();
            _termIndexFile = _termsFile = _tokensIndexFile = _tokensFile = null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    ForwardIndexSegmentReader reader() {
        return new Reader();
    }

    /**
     * A forward index reader for a single segment.
     *
     * This can be used by a single thread to read from a forward index segment.
     * Not thread-safe because it contains state (file pointers, doc offset/length).
     */
    @NotThreadSafe
    public class Reader implements ForwardIndexSegmentReader {

        private IndexInput _tokensIndex;

        private IndexInput _tokens;

        // Used by retrievePart(s)
        private long docTokensOffset;

        // Used by retrievePart(s)
        private int docLength;

        private IndexInput tokensIndex() {
            if (_tokensIndex == null)
                _tokensIndex = _tokensIndexFile.clone();
            return _tokensIndex;
        }

        private IndexInput tokens() {
            if (_tokens == null)
                _tokens = _tokensFile.clone();
            return _tokens;
        }

        /** Retrieve parts of a document from the forward index. */
        @Override
        public List<int[]> retrieveParts(String luceneField, int docId, int[] starts, int[] ends) {
            int n = starts.length;
            if (n != ends.length)
                throw new IllegalArgumentException("start and end must be of equal length");

            getDocOffsetAndLength(luceneField, docId);
            docLength -= BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
            tokens(); // ensure available
            List<int[]> result = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                result.add(retrievePart(starts[i], ends[i]));
            }
            return result;
        }

        /** Retrieve parts of a document from the forward index. */
        @Override
        public int[] retrievePart(String luceneField, int docId, int start, int end) {
            // ensure both inputs available
            getDocOffsetAndLength(luceneField, docId);
            docLength -= BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
            tokens(); // ensure we have this input available
            return retrievePart(start, end);
        }

        private int[] retrievePart(int start, int end) {
            if (start == -1)
                start = 0;
            if (end == -1 || end
                    > docLength) // Can happen while making KWICs because we don't know the doc length until here
                end = docLength;
            ForwardIndexAbstract.validateSnippetParameters(docLength, start, end);

            // Read the snippet from the tokens file
            try {
                _tokens.seek(docTokensOffset + (long) start * Integer.BYTES);
                int[] snippet = new int[end - start];
                for (int j = 0; j < snippet.length; j++) {
                    snippet[j] = _tokens.readInt();
                }
                return snippet;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void getDocOffsetAndLength(String luceneField, int docId)  {
            try {
                tokensIndex(); // ensure input available
                long fieldTokensIndexOffset = fields.get(luceneField).getTokensIndexOffset();
                _tokensIndex.seek(fieldTokensIndexOffset + (long) docId * Long.BYTES);
                docTokensOffset = _tokensIndex.readLong();
                long nextDocTokensOffset = _tokensIndex.readLong(); // (always exists because we write an extra value at the end)
                docLength = (int)((nextDocTokensOffset - docTokensOffset) / Integer.BYTES);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /** Get length of document in tokens from the forward index.
         *
         * This includes the "extra closing token" at the end, so subtract one for the real length.
         *
         * @param luceneField lucene field to read forward index from
         * @param docId segment-local docId of document to get length for
         * @return doc length
         */
        @Override
        public long docLength(String luceneField, int docId) {
            getDocOffsetAndLength(luceneField, docId);
            return docLength;
        }

        @Override
        public TermsSegmentReader terms(String luceneField) {
            BLTerms terms;
            try {
                terms = fieldsProducer.terms(luceneField);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return terms;
        }
    }
}
