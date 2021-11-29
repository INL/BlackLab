package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

    /** Extension for the fields file. This stores the annotated field name and the offset
        in the term index file where the term offsets ares stored.*/
    private static final String FIELDS_EXT = "fields";

    /** Extension for the term index file, that stores the offset in the terms file where
        the term strings start for each term (in each annotated field). */
    private static final String TERMINDEX_EXT = "termindex";

    /** Extension for the terms file, where the term strings are stored. */
    private static final String TERMS_EXT = "terms";

    /** Extension for the tokens index file, that stores the offsets in the tokens file
        where the tokens for each document are stored. */
    private static final String TOKENS_INDEX_EXT = "tokensindex";

    /** Extension for the tokens file, where a term id is stored for each position in each document. */
    private static final String TOKENS_EXT = "tokens";

    /** Extension for the temporary term vector file that will be converted later.
     * The term vector file contains the occurrences for each term in each doc (and each annotated field)
     */
    private static final String TERMVEC_TMP_EXT = "termvec.tmp";

    /** The FieldsConsumer we're adapting and delegating some requests to. */
    private FieldsConsumer delegateFieldsConsumer;

    /** Holds common information used for writing to index files. */
    private SegmentWriteState state;

    /** Codec name (always "BLCodec"?) */
    private String name;

    /** Name of the postings format we've adapted. */
    private String delegatePostingsFormatName;

    /**
     * Instantiates a fields consumer.
     *
     * @param fieldsConsumer FieldsConsumer to be adapted by us
     * @param state holder class for common parameters used during write
     * @param name name of our codec
     * @param delegatePostingsFormatName name of the delegate postings format
     *                                   (the one our PostingsFormat class adapts)
     */
    public BLFieldsConsumer(FieldsConsumer fieldsConsumer, SegmentWriteState state, String name,
            String delegatePostingsFormatName) {
        this.delegateFieldsConsumer = fieldsConsumer;
        this.state = state;
        this.name = name;
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
        delegateFieldsConsumer.write(fields, norms);
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

        // Write our postings extension information
        try (IndexOutput fieldsFile = openOutputFile(FIELDS_EXT);
                IndexOutput termIndexFile = openOutputFile(TERMINDEX_EXT);
                IndexOutput termsFile = openOutputFile(TERMS_EXT)) {

            // We'll keep track of doc lengths so we can preallocate our forward index structure.
            Map<Integer, Integer> docLengths = new HashMap<Integer, Integer>();

            // First we write a temporary dump of the term vector, and keep track of
            // where we can find term occurrences per document so we can reverse this
            // file later.
            // (we iterate per field & term first, because that is how Lucene's reverse
            //  index stores the information. What we need is per field, then per document
            //  (we're trying to reconstruct the document), so we will do that below.
            //   we use temporary files because this might take a huge amount of memory)
            Map<String, Map<Integer, List<Long>>> docPosOffsetsPerField = new HashMap<>();
            try (IndexOutput tempTermVectorFile = openOutputFile(TERMVEC_TMP_EXT)) {

                tempTermVectorFile.writeString(delegatePostingsFormatName);
                fieldsFile.writeInt(countAnnotationFields(fields));

                // Process fields
                for (String field: fields) { // for each field
                    // If it's (part of) an annotated field...
                    // TODO: we probably only want to create a forward index for one "alternative"
                    //   of each annotation; the case/accent-sensitive one if it exists.
                    Terms terms = fields.terms(field);
                    if (isAnnotationField(terms)) {

                        // Record field name and offset into term index file (in the fields file)
                        fieldsFile.writeString(field);
                        fieldsFile.writeLong(termIndexFile.getFilePointer());

                        // Record number of terms (in the term index file at the above offset)
                        termIndexFile.writeLong(terms.size());

                        // Keep track of where to find term positions for each document
                        // (for reversing index)
                        // The map is keyed by docId and stores a list of offsets into the
                        // temporary termvector file where the occurrences for each term can be
                        // found.
                        Map<Integer, List<Long>> docPosOffsets = docPosOffsetsPerField.get(field);
                        if (docPosOffsets == null) {
                            docPosOffsets = new HashMap<>();
                            docPosOffsetsPerField.put(field, docPosOffsets);
                        }

                        // For each term in this field...
                        PostingsEnum postingsEnum = null; // we'll reuse this for efficiency
                        TermsEnum termsEnum = terms.iterator();
                        while (true) {
                            BytesRef term = termsEnum.next();
                            if (term == null)
                                break;
                            termIndexFile.writeLong(termsFile.getFilePointer()); // where to find term string
                            termsFile.writeString(term.utf8ToString());          // term string

                            // For each document containing this term...
                            postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.POSITIONS);
                            while (true) {
                                Integer docId = postingsEnum.nextDoc();
                                if (docId.equals(DocIdSetIterator.NO_MORE_DOCS))
                                    break;

                                // Keep track of term positions offsets in term vector file
                                List<Long> vectorFileOffsets = docPosOffsets.get(docId);
                                if (vectorFileOffsets == null) {
                                    vectorFileOffsets = new ArrayList<>();
                                    docPosOffsets.put(docId, vectorFileOffsets);
                                }
                                vectorFileOffsets.add(tempTermVectorFile.getFilePointer());

                                // For each occurrence of term in this doc...
                                int nOccurrences = postingsEnum.freq();
                                tempTermVectorFile.writeInt(nOccurrences);
                                int docLength = docLengths.getOrDefault(docId, 0);
                                for (int i = 0; i < nOccurrences; i++) {
                                    int position = postingsEnum.nextPosition();
                                    if (position >= docLength)
                                        docLength = position + 1;
                                    tempTermVectorFile.writeInt(position);
                                }
                                docLengths.put(docId, docLength);
                            }
                        }
                        // Store additional metadata about this field
                        fieldInfos.fieldInfo(field).putAttribute("funFactsAboutField", "didYouKnowThat?");
                    }
                }
            }

            // Reverse the reverse index to create forward index
            // (this time we iterate per field and per document first, then reconstruct the document by
            //  looking at each term's occurrences. This produces our forward index)
            try (IndexInput inTermVectorFile = openInputFile(TERMVEC_TMP_EXT);
                    IndexOutput outTokensIndexFile = openOutputFile(TOKENS_INDEX_EXT);
                    IndexOutput outTokensFile = openOutputFile(TOKENS_EXT)) {

                // For each field...
                for (Entry<String, Map<Integer, List<Long>>> fieldEntry: docPosOffsetsPerField.entrySet()) {
                    String field = fieldEntry.getKey();
                    Map<Integer, List<Long>> docPosOffsets = fieldEntry.getValue();
                    // For each document...
                    for (Entry<Integer, List<Long>> docEntry: docPosOffsets.entrySet()) {
                        Integer docId = docEntry.getKey();
                        List<Long> termPosOffsets = docEntry.getValue();
                        int docLength = docLengths.get(docId);
                        int[] tokensInDoc = new int[docLength]; // reconstruct the document here
                        Arrays.fill(tokensInDoc, -1); // initialize to illegal value
                        // For each term...
                        int termId = 0;
                        for (Long offset: termPosOffsets) {
                            inTermVectorFile.seek(offset);
                            int nOccurrences = inTermVectorFile.readInt();
                            // For each occurrence...
                            for (int i = 0; i < nOccurrences; i++) {
                                int position = inTermVectorFile.readInt();
                                tokensInDoc[position] = termId;
                            }
                            termId++;
                        }
                        // Write the forward index for this document (reconstructed doc)
                        outTokensIndexFile.writeLong(outTokensFile.getFilePointer());
                        outTokensFile.writeInt(docLength);
                        for (int token: tokensInDoc) { // loop may be slow, writeBytes..? endianness, etc.?
                            outTokensFile.writeInt(token);
                        }
                    }
                }
            }
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        }
    }

    /**
     * Determine number of fields that are (part of) annotated fields.
     *
     * @param fields all fields
     * @return number of fields with annotations
     * @throws IOException
     */
    private int countAnnotationFields(Fields fields) throws IOException {
        int nAnnotatedFields = 0;
        for (String field: fields) {
            Terms terms = fields.terms(field);
            if (isAnnotationField(terms))
                nAnnotatedFields++;
        }
        return nAnnotatedFields;
    }

    private Boolean isAnnotationField(Terms terms) throws IOException {
        if (terms == null)
            return false;
        boolean hasPositions = terms.hasPositions();
        boolean hasFreqs = terms.hasFreqs();
        boolean isAnnotation = hasFreqs && hasPositions;
        return isAnnotation;
    }

    protected IndexOutput openOutputFile(String ext) throws IOException {
        return state.directory.createOutput(getSegmentFileName(ext), state.context);
    }

    protected IndexInput openInputFile(String ext) throws IOException {
        return state.directory.openInput(getSegmentFileName(ext), state.context);
    }

    protected String getSegmentFileName(String ext) {
        return IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, "bl" + ext);
    }

    @Override
    public void close() throws IOException {
        delegateFieldsConsumer.close();
    }

}
