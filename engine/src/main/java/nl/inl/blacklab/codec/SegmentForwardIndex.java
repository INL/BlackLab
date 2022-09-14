package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.store.IndexInput;

import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.codec.BlackLab40PostingsWriter.Field;
import nl.inl.blacklab.forwardindex.ForwardIndexAbstract;
import nl.inl.blacklab.forwardindex.ForwardIndexSegmentReader;
import nl.inl.blacklab.forwardindex.TermsSegmentReader;

/**
 * Manages read access to forward indexes for a single segment.
 */
@ThreadSafe
class SegmentForwardIndex implements AutoCloseable {

    /** Tokens index file record consists of offset in tokens file, doc length in tokens, and tokens encoding scheme. */
    private static final long TOKENS_INDEX_RECORD_SIZE = Long.BYTES + Integer.BYTES + Byte.BYTES;

    /** Our fields producer */
    private final BlackLab40PostingsReader fieldsProducer;

    /** Contains field names and offsets to term index file, where the terms for the field can be found */
    private final Map<String, Field> fieldsByName = new LinkedHashMap<>();

    /** Contains offsets into termsFile where the string for each term can be found */
    private IndexInput _termIndexFile;

    /** Contains term strings for all fields */
    private IndexInput _termsFile;

    /** Contains indexes into the tokens file for all field and documents */
    private IndexInput _tokensIndexFile;

    /** Contains the tokens for all fields and documents */
    private IndexInput _tokensFile;

    /** Contains presorted indices for the terms file */
    private IndexInput _termOrderFile;

    public SegmentForwardIndex(BlackLab40PostingsReader postingsReader) throws IOException {
        this.fieldsProducer = postingsReader;

        try (IndexInput fieldsFile = postingsReader.openIndexFile(BlackLab40PostingsFormat.FIELDS_EXT)) {
            long size = fieldsFile.length();
            while (fieldsFile.getFilePointer() < size) {
                Field f = new Field(fieldsFile);
                this.fieldsByName.put(f.getFieldName(), f);
            }
        }

        _termIndexFile = postingsReader.openIndexFile(BlackLab40PostingsFormat.TERMINDEX_EXT);
        _termsFile = postingsReader.openIndexFile(BlackLab40PostingsFormat.TERMS_EXT);
        _termOrderFile = postingsReader.openIndexFile(BlackLab40PostingsFormat.TERMORDER_EXT);
        _tokensIndexFile = postingsReader.openIndexFile(BlackLab40PostingsFormat.TOKENS_INDEX_EXT);
        _tokensFile = postingsReader.openIndexFile(BlackLab40PostingsFormat.TOKENS_EXT);
    }

    @Override
    public void close() {
        try {
            _tokensFile.close();
            _tokensIndexFile.close();
            _termsFile.close();
            _termOrderFile.close();
            _termIndexFile.close();
            _termIndexFile = _termsFile = _termOrderFile = _tokensIndexFile = _tokensFile = null;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    ForwardIndexSegmentReader reader() {
        return new Reader();
    }

    public static class SegmentTerm {
        public String term;
        public int segmentTermID;
        public int segmentTermOrderSensitive;
        public int segmentTermOrderInsensitive;
    }


    /** 
     * Whereas SegmentForwardIndex manages the entire forward index for an entire segment (LeafReader),
     * This manages the forward index for a specific annotation.
     */
    public class SegmentForwardIndexField {
        private Field field;

        public SegmentForwardIndexField(Field field) {
            this.field = field;
        }
        // ahh yes, this is for a single field only.
        // so we should be able to do this properly.

        // so where do we orchestrate the global list?
        // ideally we'd store the term to global list in this object here
        // so we don't need it in the TermsIntegrated class.

        // MISSING:
        // interaction from *this* -> TermsIntegrated (i.e. local 2 global magic)

        // what do we need to do to perform that?
        // generate the following fields:
        // - segmentToGlobalTermIds
        // - terms (global list, any order as long as the sort arrays are correct)
        // - termId2SensitivePosition
        // - termId2InsensitivePosition



        // how do we do this?
        // we have the smart merge algo
        // where to put it?


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

        // Used by retrievePart(s)
        private TokensEncoding tokensEncoding;

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

            // We don't exclude the closing token here because we didn't do that with the external index format either.
            // And you might want to fetch the extra closing token.
            //docLength -= BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
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
            // We don't exclude the closing token here because we didn't do that with the external index format either.
            // And you might want to fetch the extra closing token.
            //docLength -= BlackLabIndexAbstract.IGNORE_EXTRA_CLOSING_TOKEN;
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
                int[] snippet = new int[end - start];
                switch (tokensEncoding) {
                case INT_PER_TOKEN:
                    // Simplest encoding, just one 4-byte int per token
                    _tokens.seek(docTokensOffset + (long) start * Integer.BYTES);
                    for (int j = 0; j < snippet.length; j++) {
                        snippet[j] = _tokens.readInt();
                    }
                    break;
                case ALL_TOKENS_THE_SAME:
                    // All tokens have the same value, so we only have one value stored
                    _tokens.seek(docTokensOffset);
                    int value = _tokens.readInt();
                    Arrays.fill(snippet, value);
                    break;
                default:
                    throw new UnsupportedOperationException("Cannot read tokens encoding: " + tokensEncoding);
                }
                return snippet;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private void getDocOffsetAndLength(String luceneField, int docId)  {
            try {
                tokensIndex(); // ensure input available
                long fieldTokensIndexOffset = fieldsByName.get(luceneField).getTokensIndexOffset();
                _tokensIndex.seek(fieldTokensIndexOffset + (long) docId * TOKENS_INDEX_RECORD_SIZE);
                docTokensOffset = _tokensIndex.readLong();
                docLength = _tokensIndex.readInt();
                tokensEncoding = TokensEncoding.fromCode(_tokensIndex.readByte());
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
            try {
                return fieldsProducer.terms(luceneField);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
