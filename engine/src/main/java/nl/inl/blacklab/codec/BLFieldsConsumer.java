package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.NormsProducer;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.Fields;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.MappedMultiFields;
import org.apache.lucene.index.MergeState;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.ReaderSlice;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;

import nl.inl.blacklab.exceptions.BlackLabRuntimeException;

/**
 * BlackLab FieldsConsumer: writes postings information to the index,
 * using a delegate and extending its functionality by also writing a forward
 * index.
 *
 * Adapted from <a href="https://github.com/meertensinstituut/mtas/">MTAS</a>.
 */
public class BLFieldsConsumer extends FieldsConsumer {

    protected static final Logger logger = LogManager.getLogger(BLFieldsConsumer.class);

    private final int NO_TERM = nl.inl.blacklab.forwardindex.Terms.NO_TERM;

    /** The FieldsConsumer we're adapting and delegating some requests to. */
    private final FieldsConsumer delegateFieldsConsumer;

    /** Holds common information used for writing to index files. */
    private final SegmentWriteState state;

    /** Codec name (always "BLCodec"?) */
    private final String codecName;

    /** Name of the postings format we've adapted. */
    private final String delegatePostingsFormatName;

    /**
     * Instantiates a fields consumer.
     *
     * @param fieldsConsumer FieldsConsumer to be adapted by us
     * @param state holder class for common parameters used during write
     * @param codecName name of our codec
     * @param delegatePostingsFormatName name of the delegate postings format
     *                                   (the one our PostingsFormat class adapts)
     */
    public BLFieldsConsumer(FieldsConsumer fieldsConsumer, SegmentWriteState state, String codecName,
            String delegatePostingsFormatName) {
        this.delegateFieldsConsumer = fieldsConsumer;
        this.state = state;
        this.codecName = codecName;
        this.delegatePostingsFormatName = delegatePostingsFormatName;
    }

    /**
     * Merges in the fields from the readers in <code>mergeState</code>.
     *
     * Identical to {@link FieldsConsumer#merge}, essentially cancelling the delegate's
     * own merge method, e.g. FieldsWriter#merge in
     * {@link org.apache.lucene.codecs.perfield.PerFieldPostingsFormat}}.
     *
     * As suggested by the name and above comments, this seems to be related to segment merging.
     * Notice the call to write() at the end of the method, writing the merged segment to disk.
     *
     * (not sure why this is done; presumably the overridden merge method caused problems?
     * the javadoc for FieldsConsumer's version does mention that subclasses can provide more sophisticated
     * merging; maybe that interferes with this FieldsConsumer's customizations?)
     */
    @Override
    public void merge(MergeState mergeState, NormsProducer norms) throws IOException {
        final List<Fields> fields = new ArrayList<>();
        final List<ReaderSlice> slices = new ArrayList<>();

        int docBase = 0;

        for (int readerIndex = 0; readerIndex < mergeState.fieldsProducers.length; readerIndex++) {
            final FieldsProducer f = mergeState.fieldsProducers[readerIndex];

            final int maxDoc = mergeState.maxDocs[readerIndex];
            f.checkIntegrity();
            slices.add(new ReaderSlice(docBase, maxDoc, readerIndex));
            fields.add(f);
            docBase += maxDoc;
        }

        Fields mergedFields = new MappedMultiFields(mergeState,
                new MultiFields(fields.toArray(Fields.EMPTY_ARRAY),
                        slices.toArray(ReaderSlice.EMPTY_ARRAY)));
        write(mergedFields, norms);
    }

    /**
     * Called by Lucene to write fields, terms and postings.
     *
     * Seems to be called whenever a segment is written, either initially or after
     * a segment merge.
     *
     * Delegates to the default fields consumer, but also uses the opportunity
     * to write our forward index.
     *
     * @param fields fields to write
     * @param norms norms (not used by us)
     */
    @Override
    public void write(Fields fields, NormsProducer norms) throws IOException {

        // implement custom type of stored field?

        // TODO: wrap fields to filter out content store fields (that will be handled in our own write method)
        delegateFieldsConsumer.write(fields, norms);

        // TODO: expand write() to recognize content store fields and write those to a content store file
        write(state.fieldInfos, fields);
    }

    /**
     * Write our additions to the default postings (i.e. the forward index and various trees)
     *
     * Iterates over the term vector to build the forward index in a temporary file.
     *
     * Tokens are sorted by field, term, doc, then position, so not by field, doc, position as
     * you might expect with a forward index. This is a temporary measure for efficiency.
     *
     * The second pass links all the doc+position for each term together and writes them to another
     * temporary file.
     *
     * Finally, everything is written to the final objects file in the correct order.
     *
     * This method also records metadata about fields in the FieldInfo attributes.
     */
    private void write(FieldInfos fieldInfos, Fields fields) {

        try (IndexOutput outTokensIndexFile = createOutput(BLCodecPostingsFormat.TOKENS_INDEX_EXT);
                IndexOutput outTokensFile = createOutput(BLCodecPostingsFormat.TOKENS_EXT)) {

            // Keep track of starting offset in termindex and tokensindex files per field
            Map<String, Long> field2TermIndexOffsets = new HashMap<>();
            Map<String, Long> field2TokensIndexOffsets = new HashMap<>();

            // Write our postings extension information
            try (IndexOutput termIndexFile = createOutput(BLCodecPostingsFormat.TERMINDEX_EXT);
                    IndexOutput termsFile = createOutput(BLCodecPostingsFormat.TERMS_EXT)) {

                // We'll keep track of doc lengths so we can preallocate our forward index structure.
                Map<Integer, Integer> docLengths = new HashMap<>();

                // First we write a temporary dump of the term vector, and keep track of
                // where we can find term occurrences per document so we can reverse this
                // file later.
                // (we iterate per field & term first, because that is how Lucene's reverse
                //  index stores the information. What we need is per field, then per document
                //  (we're trying to reconstruct the document), so we will do that below.
                //   we use temporary files because this might take a huge amount of memory)
                // (use a LinkedHashMap to maintain the same field order when we write the tokens below)
                Map<String, SortedMap<Integer, Map<Integer, Long>>> field2docTermVecFileOffsets = new LinkedHashMap<>();
                try (IndexOutput outTempTermVectorFile = createOutput(BLCodecPostingsFormat.TERMVEC_TMP_EXT)) {

                    // Process fields
                    for (String field: fields) { // for each field
                        // If it's (part of) an annotated field...
                        // TODO: we probably only want to create a forward index for one sensitivity
                        //   of each annotation; the case/accent-sensitive one if it exists.
                        //   we need the index metadata for this though.
                        if (isAnnotationField(field)) {

                            Terms terms = fields.terms(field);

                            // Record starting offset of field in termindex file (written to fields file later)
                            field2TermIndexOffsets.put(field, termIndexFile.getFilePointer());

                            // Keep track of where to find term positions for each document
                            // (for reversing index)
                            // The map is keyed by docId and stores a list of offsets into the
                            // temporary termvector file where the occurrences for each term can be
                            // found.
                            Map<Integer, Map<Integer, Long>> docId2TermVecFileOffsets =
                                    field2docTermVecFileOffsets.computeIfAbsent(field, k -> new TreeMap<>());
                            // keep docs in order

                            // For each term in this field...
                            PostingsEnum postingsEnum = null; // we'll reuse this for efficiency
                            TermsEnum termsEnum = terms.iterator();
                            int termId = 0;
                            while (true) {
                                BytesRef term = termsEnum.next();
                                if (term == null)
                                    break;
                                termIndexFile.writeLong(termsFile.getFilePointer()); // where to find term string
                                String termString = term.utf8ToString();
                                termsFile.writeString(termString);          // term string

                                // For each document containing this term...
                                postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.POSITIONS);
                                while (true) {
                                    Integer docId = postingsEnum.nextDoc();
                                    if (docId.equals(DocIdSetIterator.NO_MORE_DOCS))
                                        break;

                                    // Keep track of term positions offsets in term vector file
                                    Map<Integer, Long> vecFileOffsetsPerTermId =
                                            docId2TermVecFileOffsets.computeIfAbsent(docId, k -> new HashMap<>());
                                    vecFileOffsetsPerTermId.put(termId, outTempTermVectorFile.getFilePointer());

                                    // For each occurrence of term in this doc...
                                    int nOccurrences = postingsEnum.freq();
                                    outTempTermVectorFile.writeInt(nOccurrences);
                                    int docLength = docLengths.getOrDefault(docId, 0);
                                    for (int i = 0; i < nOccurrences; i++) {
                                        int position = postingsEnum.nextPosition();
                                        if (position >= docLength)
                                            docLength = position + 1;
                                        outTempTermVectorFile.writeInt(position);
                                    }
                                    docLengths.put(docId, docLength);
                                }
                                termId++;
                            }
                            // Store additional metadata about this field
                            fieldInfos.fieldInfo(field).putAttribute("funFactsAboutField", "didYouKnowThat?");
                        }
                    }
                }

                // Reverse the reverse index to create forward index
                // (this time we iterate per field and per document first, then reconstruct the document by
                //  looking at each term's occurrences. This produces our forward index)
                try (IndexInput inTermVectorFile = openInput(BLCodecPostingsFormat.TERMVEC_TMP_EXT)) {

                    // For each field...
                    for (Entry<String, SortedMap<Integer, Map<Integer, Long>>> fieldEntry: field2docTermVecFileOffsets.entrySet()) {
                        String field = fieldEntry.getKey();
                        SortedMap<Integer, Map<Integer, Long>> docPosOffsets = fieldEntry.getValue();

                        // Record starting offset of field in tokensindex file (written to fields file later)
                        field2TokensIndexOffsets.put(field, outTokensIndexFile.getFilePointer());

                        // For each document...
                        int expectedDocId = 0; // make sure we catch it if some doc(s) have no values for this field
                        for (Entry<Integer, Map<Integer, Long>> docEntry: docPosOffsets.entrySet()) {
                            Integer docId = docEntry.getKey();

                            // If there were docs that did not contain values for this field,
                            // write empty values.
                            // TODO: optimize this so we don't waste disk space in this case (although probably rare)
                            while (docId > expectedDocId) {
                                // entire document is missing values
                                outTokensIndexFile.writeLong(outTokensFile.getFilePointer());
                                for (int i = 0; i < docLengths.get(docId); i++) {
                                    outTokensFile.writeInt(NO_TERM);
                                }
                                expectedDocId++;
                            }
                            if (docId != expectedDocId)
                                throw new RuntimeException("Expected docId " + expectedDocId + ", got " + docId);
                            expectedDocId++;

                            Map<Integer, Long> termPosOffsets = docEntry.getValue();
                            int docLength = docLengths.get(docId);
                            int[] tokensInDoc = new int[docLength]; // reconstruct the document here
                            Arrays.fill(tokensInDoc, NO_TERM); // initialize to illegal value
                            // For each term...
                            for (Map.Entry<Integer, Long> e: termPosOffsets.entrySet()) {
                                int termId = e.getKey();
                                inTermVectorFile.seek(e.getValue());
                                int nOccurrences = inTermVectorFile.readInt();
                                // For each occurrence...
                                for (int i = 0; i < nOccurrences; i++) {
                                    int position = inTermVectorFile.readInt();
                                    tokensInDoc[position] = termId;
                                }
                            }
                            // Write the forward index for this document (reconstructed doc)
                            outTokensIndexFile.writeLong(outTokensFile.getFilePointer());
                            for (int token: tokensInDoc) { // loop may be slow, writeBytes..? endianness, etc.?
                                outTokensFile.writeInt(token);
                            }
                        }
                        // Finally write offset after last doc (for calculating doc length of last doc)
                        outTokensIndexFile.writeLong(outTokensFile.getFilePointer());
                    }
                } finally {
                    // Clean up after ourselves
                    deleteIndexFile(BLCodecPostingsFormat.TERMVEC_TMP_EXT);
                }
            }

            // Write fields file, now that we know all the relevant offsets
            try (IndexOutput fieldsFile = createOutput(BLCodecPostingsFormat.FIELDS_EXT)) {
                for (String field: fields) { // for each field
                    // If it's (part of) an annotated field...
                    if (field2TermIndexOffsets.containsKey(field)) {
                        // Record field name and offset into term index and tokens index files
                        fieldsFile.writeString(field);
                        fieldsFile.writeLong(field2TermIndexOffsets.get(field));
                        fieldsFile.writeLong(field2TokensIndexOffsets.get(field));
                    }
                }
            }

        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    private Boolean isAnnotationField(String field) throws IOException {
        return field != null && field.contains("%") && field.contains("@");
    }

    private String getSegmentFileName(String ext) {
        return IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, "bl" + ext);
    }

    private IndexOutput createOutput(String ext) throws IOException {
        IndexOutput output = state.directory.createOutput(getSegmentFileName(ext), state.context);

        // Write standard header, with the codec name and version, segment info.
        // Also write the delegate codec name (Lucene's default codec).
        CodecUtil.writeIndexHeader(output, codecName, BLCodecPostingsFormat.VERSION_CURRENT,
                state.segmentInfo.getId(), state.segmentSuffix);
        output.writeString(delegatePostingsFormatName);

        return output;
    }

    private IndexInput openInput(String ext) throws IOException {
        String fileName = getSegmentFileName(ext);
        IndexInput input = state.directory.openInput(getSegmentFileName(ext), state.context);

        // Read and check standard header, with codec name and version and segment info.
        // Also check the delegate codec name (should be the expected version of Lucene's codec).
        CodecUtil.checkIndexHeader(input, codecName, BLCodecPostingsFormat.VERSION_START,
                BLCodecPostingsFormat.VERSION_CURRENT, state.segmentInfo.getId(), state.segmentSuffix);
        String delegatePFN = input.readString();
        if (!delegatePostingsFormatName.equals(delegatePFN))
            throw new IOException("Segment file " + fileName +
                    " contains wrong delegate postings format name: " + delegatePFN +
                    " (expected " + delegatePostingsFormatName + ")");

        return input;
    }

    private void deleteIndexFile(String ext) throws IOException {
        state.directory.deleteFile(getSegmentFileName(ext));
    }

    @Override
    public void close() throws IOException {
        delegateFieldsConsumer.close();
    }

}
