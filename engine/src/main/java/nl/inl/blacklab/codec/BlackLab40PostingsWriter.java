package nl.inl.blacklab.codec;

import java.io.IOException;
import java.text.CollationKey;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.lang3.NotImplementedException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.NormsProducer;
import org.apache.lucene.index.FieldInfo;
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
import org.apache.lucene.store.ByteArrayDataInput;
import org.apache.lucene.store.ByteArrayDataOutput;
import org.apache.lucene.store.DataOutput;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.util.BytesRef;

import it.unimi.dsi.fastutil.ints.IntArrays;
import nl.inl.blacklab.analysis.PayloadUtils;
import nl.inl.blacklab.codec.TokensCodec.VALUE_PER_TOKEN_PARAMETER;
import nl.inl.blacklab.exceptions.BlackLabRuntimeException;
import nl.inl.blacklab.forwardindex.Collators;
import nl.inl.blacklab.search.BlackLabIndexIntegrated;
import nl.inl.blacklab.search.indexmetadata.AnnotatedFieldNameUtil;
import nl.inl.blacklab.search.indexmetadata.MatchSensitivity;
import nl.inl.blacklab.search.indexmetadata.RelationUtil;
import nl.inl.blacklab.search.lucene.RelationInfo;

/**
 * BlackLab FieldsConsumer: writes postings information to the index,
 * using a delegate and extending its functionality by also writing a forward
 * index.
 *
 * Adapted from <a href="https://github.com/meertensinstituut/mtas/">MTAS</a>.
 */
public class BlackLab40PostingsWriter extends FieldsConsumer {

    protected static final Logger logger = LogManager.getLogger(BlackLab40PostingsWriter.class);

    private static final int NO_TERM = nl.inl.blacklab.forwardindex.Terms.NO_TERM;

    /** The FieldsConsumer we're adapting and delegating some requests to. */
    private final FieldsConsumer delegateFieldsConsumer;

    /** Holds common information used for writing to index files. */
    private final SegmentWriteState state;

    /** Name of the postings format we've adapted. */
    private final String delegatePostingsFormatName;

    /**
     * Instantiates a fields consumer.
     *
     * @param delegateFieldsConsumer FieldsConsumer to be adapted by us
     * @param state holder class for common parameters used during write
     * @param delegatePostingsFormatName name of the delegate postings format
     *                                   (the one our PostingsFormat class adapts)
     */
    public BlackLab40PostingsWriter(FieldsConsumer delegateFieldsConsumer, SegmentWriteState state,
            String delegatePostingsFormatName) {
        this.delegateFieldsConsumer = delegateFieldsConsumer;
        this.state = state;
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
        write(state.fieldInfos, fields);
        delegateFieldsConsumer.write(fields, norms);
    }

    /** 
     * Information about a Lucene field that represents a BlackLab annotation in the forward index.
     * A Field's information is only valid for the segment (leafreadercontext) of the index it was read from.
     * Contains offsets into files comprising the terms strings and forward index information.
     * Such as where in the term strings file the strings for this field begin.
     * See integrated.md
    */
    public static class Field {
        private final String fieldName;
        protected int numberOfTerms;
        protected long termOrderOffset;
        protected long termIndexOffset;
        protected long tokensIndexOffset;

        protected Field(String fieldName) {  this.fieldName = fieldName; }
        
        /** Read our values from the file */
        public Field(IndexInput file) throws IOException {
            this.fieldName = file.readString();
            this.numberOfTerms = file.readInt();
            this.termOrderOffset = file.readLong();
            this.termIndexOffset = file.readLong();
            this.tokensIndexOffset = file.readLong();
        }

        public Field(String fieldName, int numberOfTerms, long termIndexOffset, long termOrderOffset, long tokensIndexOffset) {
            this.fieldName = fieldName;
            this.numberOfTerms = numberOfTerms;
            this.termOrderOffset = termOrderOffset;
            this.termIndexOffset = termIndexOffset;
            this.tokensIndexOffset = tokensIndexOffset;
        }

        public String getFieldName() { return fieldName; }
        public int getNumberOfTerms() { return numberOfTerms; }
        public long getTermIndexOffset() { return termIndexOffset; }
        public long getTermOrderOffset() { return termOrderOffset; }
        public long getTokensIndexOffset() { return tokensIndexOffset; }

        public void write(IndexOutput file) throws IOException {
            file.writeString(getFieldName());
            file.writeInt(getNumberOfTerms());
            file.writeLong(getTermOrderOffset());
            file.writeLong(getTermIndexOffset());
            file.writeLong(getTokensIndexOffset());
        }
    }

    private static class FieldMutable extends Field {
        public FieldMutable(String fieldName) { super(fieldName); }

        public void setNumberOfTerms(int number) { this.numberOfTerms = number; }
        public void setTermIndexOffset(long offset) { this.termIndexOffset = offset; }
        public void setTermOrderOffset(long offset) { this.termOrderOffset = offset; }
        public void setTokensIndexOffset(long offset) { this.tokensIndexOffset = offset; }
    }

    /**
     * Offset in term vector file per term id for a single doc and field.
     *
     * Keeps track of position in the term vector file where the occurrences of each term id
     * are stored for a single document and field.
     */
    private static class TermVecFileOffsetPerTermId extends LinkedHashMap<Integer, Long> {
        // intentionally blank; this is just a typedef for readability
    }

    /**
     * Keeps track of tokens length and term vector file offsets for a single Lucene field.
     *
     * Tokens length is shared between all Lucene fields belonging to a single annotated
     * field, because those all have the same length.
     */
    private static class LengthsAndOffsetsPerDocument {
        /** For each document id (key), the length in tokens of the annotated field this Lucene field is a part of.
         *  (shared with other annotations on this annotated field) */
        private final Map<Integer, Integer> docId2annotatedFieldTokenLengths;

        /** For each document id (key), record the term vector file offsets (value). */
        private final SortedMap<Integer, TermVecFileOffsetPerTermId> docId2TermVecFileOffsets = new TreeMap<>();

        LengthsAndOffsetsPerDocument(Map<Integer, Integer> docId2annotatedFieldTokenLengths) {
            this.docId2annotatedFieldTokenLengths = docId2annotatedFieldTokenLengths;
        }

        void putTermOffset(int docId, int termId, long filePointer) {
            TermVecFileOffsetPerTermId vecFileOffsetsPerTermId =
                    docId2TermVecFileOffsets.computeIfAbsent(docId, k -> new TermVecFileOffsetPerTermId());
            vecFileOffsetsPerTermId.put(termId, filePointer);
        }

        void updateFieldLength(int docId, int length) {
            docId2annotatedFieldTokenLengths.compute(docId, (k, v) -> v == null ? length : Math.max(v, length));
        }

        TermVecFileOffsetPerTermId get(int docId) {
            TermVecFileOffsetPerTermId termVecFileOffsetPerTermId = docId2TermVecFileOffsets.get(docId);
            if (termVecFileOffsetPerTermId == null) {
                termVecFileOffsetPerTermId = new TermVecFileOffsetPerTermId();
            }
            return termVecFileOffsetPerTermId;
        }
    }

    interface TermOccurrenceAction extends AutoCloseable {
        boolean startField(FieldInfo fieldInfo);
        void endField() throws IOException;
        void startTerm(BytesRef term) throws IOException;
        void endTerm();
        void startDocument(int docId, int nOccurrences);
        void endDocument() throws IOException;
        void termOccurrence(int position, BytesRef payload) throws IOException;
        void finalize() throws IOException;

        @Override
        void close() throws IOException;
    }

    class ForwardIndexWriter implements TermOccurrenceAction {

        Map<String, FieldMutable> fiFields = new HashMap<>();

        private final IndexOutput outTokensIndexFile = createOutput(BlackLab40PostingsFormat.FI_TOKENS_INDEX_EXT);
        private final IndexOutput outTokensFile = createOutput(BlackLab40PostingsFormat.FI_TOKENS_EXT);
        private final IndexOutput termIndexFile = createOutput(BlackLab40PostingsFormat.FI_TERMINDEX_EXT);
        private final IndexOutput termsFile = createOutput(BlackLab40PostingsFormat.FI_TERMS_EXT);
        private final IndexOutput termsOrderFile = createOutput(BlackLab40PostingsFormat.FI_TERMORDER_EXT);

        // We'll keep track of doc lengths so we can preallocate our forward index structure.
        // (we do this per annotated field, e.g. contents, NOT per annotation, e.g. contents%word@s,
        //  because all annotations on the same field have the same length)

        /** Doc lengths per annotated field (e.g. "contents") */
        private final Map<String, Map<Integer, Integer>> docLengthsPerAnnotatedField = new HashMap<>();

        /** Keep track of where we can find term occurrences per document so we can reverse term vector
            file later. */
        private final Map<String, LengthsAndOffsetsPerDocument> field2docTermVecFileOffsets = new LinkedHashMap<>();

        // First we write a temporary dump of the term vector, and keep track of
        // where we can find term occurrences per document so we can reverse this
        // file later.
        // (we iterate per field & term first, because that is how Lucene's reverse
        //  index stores the information. What we need is per field, then per document
        //  (we're trying to reconstruct the document), so we will do that below.
        //   we use temporary files because this might take a huge amount of memory)
        // (use a LinkedHashMap to maintain the same field order when we write the tokens below)

        /** Temporary term vector file that will be reversed to form the forward index */
        private final IndexOutput outTempTermVectorFile = createOutput(BlackLab40PostingsFormat.FI_TERMVEC_TMP_EXT);


        // Per field

        /** Term index offset */
        FieldMutable offsets;

        LengthsAndOffsetsPerDocument lengthsAndOffsetsPerDocument;

        List<String> termsList;


        // Per term

        int currentTermId;


        // Per document

        private int currentDocId;

        int currentDocLength;

        byte[] currentDocPositionsArray;

        DataOutput currentDocPositionsOutput;

        int currentDocOccurrencesWritten;

        ForwardIndexWriter() throws IOException {
        }

        @Override
        public void close() throws IOException {
            termsOrderFile.close();
            termsFile.close();
            termIndexFile.close();
            outTokensFile.close();
            outTokensIndexFile.close();
        }

        public boolean startField(FieldInfo fieldInfo) {

            // Should this field get a forward index?
            boolean storeForwardIndex = BlackLabIndexIntegrated.isForwardIndexField(fieldInfo);
            if (!storeForwardIndex)
                return false;

            // Make sure doc lengths are shared between all annotations for a single annotated field.
            Map<Integer, Integer> docLengths = docLengthsPerAnnotatedField.computeIfAbsent(
                    AnnotatedFieldNameUtil.getBaseName(fieldInfo.name), __ -> new HashMap<>());

            // Write the term vector file and keep track of where we can find term occurrences per document,
            // so we can turn this into the actual forward index below.
            offsets = fiFields.computeIfAbsent(fieldInfo.name, FieldMutable::new);

            // We're creating a forward index for this field.
            // That also means that the payloads will include an "is-primary-value" indicator,
            // so we know which value to store in the forward index (the primary value, i.e.
            // the first value, to be used in concordances, sort, group, etc.).
            // We must skip these indicators when working with other payload info. See PayloadUtils
            // for details.

            // Record starting offset of field in termindex file (written to fields file later)
            offsets.setTermIndexOffset(termIndexFile.getFilePointer());

            // Keep track of where to find term positions for each document
            // (for reversing index)
            // The map is keyed by docId and stores a list of offsets into the
            // temporary termvector file where the occurrences for each term can be
            // found.
            lengthsAndOffsetsPerDocument =
                    field2docTermVecFileOffsets.computeIfAbsent(fieldInfo.name,
                            __ -> new LengthsAndOffsetsPerDocument(docLengths));

            termsList = new ArrayList<>();

            currentTermId = 0;
            return true;
        }

        public void endField() throws IOException {
            // begin writing term IDs and sort orders
            Collators collators = Collators.defaultCollator();
            int[] sensitivePos2TermID = getTermSortOrder(termsList, collators.get(MatchSensitivity.SENSITIVE));
            int[] insensitivePos2TermID = getTermSortOrder(termsList, collators.get(MatchSensitivity.INSENSITIVE));
            int[] termID2SensitivePos = invert(termsList, sensitivePos2TermID, collators.get(MatchSensitivity.SENSITIVE));
            int[] termID2InsensitivePos = invert(termsList, insensitivePos2TermID, collators.get(MatchSensitivity.INSENSITIVE));

            int numTerms = termsList.size();
            offsets.setNumberOfTerms(numTerms);
            offsets.setTermOrderOffset(termsOrderFile.getFilePointer());
            // write out, specific order.
            for (int i : termID2InsensitivePos) termsOrderFile.writeInt(i);
            for (int i : insensitivePos2TermID) termsOrderFile.writeInt(i);
            for (int i : termID2SensitivePos) termsOrderFile.writeInt(i);
            for (int i : sensitivePos2TermID) termsOrderFile.writeInt(i);
        }

        public void startTerm(BytesRef term) throws IOException {
            // Write the term to the terms file
            String termString = term.utf8ToString();
            termIndexFile.writeLong(termsFile.getFilePointer()); // where to find term string
            termsFile.writeString(termString);
            termsList.add(termString);
        }

        public void endTerm() {
            currentTermId++;
        }

        public void startDocument(int docId, int nOccurrences) {
            // Keep track of term positions offsets in term vector file
            this.currentDocId = docId;
            lengthsAndOffsetsPerDocument.putTermOffset(docId, currentTermId,
                    outTempTermVectorFile.getFilePointer());

            currentDocLength = -1;
            currentDocPositionsArray = new byte[nOccurrences * Integer.BYTES];
            currentDocPositionsOutput = new ByteArrayDataOutput(currentDocPositionsArray);
            currentDocOccurrencesWritten = 0;
        }

        public void endDocument() throws IOException {
            if (currentDocLength > -1)
                lengthsAndOffsetsPerDocument.updateFieldLength(currentDocId, currentDocLength);

            // Write the positions where this term occurs as primary value
            // (will be reversed below to get the forward index)
            outTempTermVectorFile.writeInt(currentDocOccurrencesWritten);
            if (currentDocOccurrencesWritten > 0) {
                outTempTermVectorFile.writeBytes(currentDocPositionsArray, 0,
                        currentDocOccurrencesWritten * Integer.BYTES);
            }

        }

        public void termOccurrence(int position, BytesRef payload) throws IOException {
            // Go through each occurrence of term in this doc,
            // gathering the positions where this term occurs as a "primary value"
            // (the first value at this token position, which we will store in the
            //  forward index). Also determine docLength.
            if (position >= currentDocLength)
                currentDocLength = position + 1;
            // Is this a primary value or a secondary one?
            // Primary values are e.g. the original word from the document,
            // and will be stored in the forward index to be used for concordances,
            // sorting and grouping. Secondary values may be synonyms or stemmed versions
            // and will not be stored in the forward index.
            if (PayloadUtils.isPrimaryValue(payload)) {
                // primary value; write to buffer
                currentDocPositionsOutput.writeInt(position);
                currentDocOccurrencesWritten++;
            }
        }

        public void finalize() throws IOException {
            CodecUtil.writeFooter(outTempTermVectorFile);
            outTempTermVectorFile.close();

            // Reverse the reverse index to create forward index
            // (this time we iterate per field and per document first, then reconstruct the document by
            //  looking at each term's occurrences. This produces our forward index)
            try (IndexInput inTermVectorFile = openInput(BlackLab40PostingsFormat.FI_TERMVEC_TMP_EXT)) {

                // For each field...
                for (Entry<String, LengthsAndOffsetsPerDocument> fieldEntry: field2docTermVecFileOffsets.entrySet()) {
                    String luceneField = fieldEntry.getKey();
                    LengthsAndOffsetsPerDocument docPosOffsets = fieldEntry.getValue();

                    // Record starting offset of field in tokensindex file (written to fields file later)
                    fiFields.get(luceneField).setTokensIndexOffset(outTokensIndexFile.getFilePointer());

                    // Make sure we know our document lengths
                    String annotatedFieldName = AnnotatedFieldNameUtil.getBaseName(luceneField);
                    Map<Integer, Integer> docLengths = docLengthsPerAnnotatedField.get(annotatedFieldName);

                    // For each document...
                    for (int docId = 0; docId < state.segmentInfo.maxDoc(); docId++) {
                        final int docLength = docLengths.getOrDefault(docId, 0);
                        TermVecFileOffsetPerTermId offsets = docPosOffsets.get(docId);
                        int[] termIds = getDocumentContents(docLength, inTermVectorFile, offsets);
                        writeTokensInDoc(outTokensIndexFile, outTokensFile, termIds);
                    }
                }
            } finally {
                // Clean up after ourselves
                deleteIndexFile(BlackLab40PostingsFormat.FI_TERMVEC_TMP_EXT);
            }

            // Write fields file, now that we know all the relevant offsets
            try (IndexOutput fieldsFile = createOutput(BlackLab40PostingsFormat.FI_FIELDS_EXT)) {
                // for each field that has a forward index...
                for (Field field : fiFields.values()) {
                    // write the information to fields file, see integrated.md
                    field.write(fieldsFile);
                }
                CodecUtil.writeFooter(fieldsFile);
            }

            CodecUtil.writeFooter(outTokensIndexFile);
            CodecUtil.writeFooter(outTokensFile);
            CodecUtil.writeFooter(termIndexFile);
            CodecUtil.writeFooter(termsFile);
            CodecUtil.writeFooter(termsOrderFile);
        }
    }

    private class RelationInfoWriter implements TermOccurrenceAction {

        /** Information per unique relation id.
         *  for each document and relation id: offset in attrset file */
        private final IndexOutput outDocsFile = createOutput(BlackLab40PostingsFormat.RI_DOCS_EXT);

        /** Information per unique relation id.
         *  for each document and relation id: offset in attrset file */
        private final IndexOutput outRelationsFile = createOutput(BlackLab40PostingsFormat.RI_RELATIONS_EXT);

        /** Attribute sets files.
         * Contains:
         * - list of attribute names (will be read into memory on index opening)
         * - for each attribute in each set: attr name index and index in attr string offsets file */
        private final IndexOutput outAttrSetsFile = createOutput(BlackLab40PostingsFormat.RI_ATTR_SETS_EXT);

        /** All the attribute names (will be read into memory on index opening) */
        private final IndexOutput outAttrNamesFile = createOutput(BlackLab40PostingsFormat.RI_ATTR_NAMES_EXT);

        /** Attribute values (strings) */
        private final IndexOutput outAttrValuesFile = createOutput(BlackLab40PostingsFormat.RI_ATTR_VALUES_EXT);

        /** Index of attribute name in attrnames file */
        private final Map<String, Integer> indexFromAttributeName = new HashMap<>();

        /** Offsets of attribute values in attrvalues file */
        private final Map<String, Long> attrValueOffsets = new HashMap<>();

        /** Offsets of attribute set in the attrsets file. */
        private Map<SortedMap<Integer, Long>, Long> idFromAttributeSet = new HashMap<>();

        // PER TERM

        /** Offset of attribute set for current term in attrset file */
        private long currentTermAttrSetOffset = -1;

        // PER DOCUMENT

        /** Offset in attrset file for each relation id in current doc */
        private ArrayList<Long> currentDocAttrSetOffsetPerRelationId;

        RelationInfoWriter() throws IOException {
        }

        @Override
        public void close() throws IOException {
            // Close the index files we've been writing to
            outDocsFile.close();
            outRelationsFile.close();
            outAttrSetsFile.close();
            outAttrNamesFile.close();
            outAttrValuesFile.close();
        }

        @Override
        public boolean startField(FieldInfo fieldInfo) {
            // Is this the relation annotation? Then we want to store relation info such as attribute values,
            // so we can look them up for individual relations matched.
            boolean storeRelationInfo = false;
            String[] nameComponents = AnnotatedFieldNameUtil.getNameComponents(fieldInfo.name);
            if (nameComponents.length > 1 && AnnotatedFieldNameUtil.isRelationAnnotation(nameComponents[1])) {
                // Yes, store relation info.
                storeRelationInfo = true;
            }
            return storeRelationInfo;
        }

        @Override
        public void endField() throws IOException {

        }

        @Override
        public void startTerm(BytesRef term) throws IOException {
            String termStr = term.utf8ToString();
            boolean ignoreTerm = RelationUtil.isOptimizationTerm(termStr);
            if (!ignoreTerm) {
                // Decode the term so we can store the attribute values and refer to them from each occurrence by relation id.
                Map<String, String> attributes = RelationUtil.attributesFromIndexedTerm(termStr);
                SortedMap<Integer, Long> currentTermAttributes = new TreeMap<>();
                for (Entry<String, String> e: attributes.entrySet()) {
                    String attrName = e.getKey();
                    String attrValue = e.getValue();
                    // Look up the attribute name index, storing the attribute name if this is the first time we see it
                    int attrNameIndex = indexFromAttributeName.computeIfAbsent(attrName, k -> {
                        try {
                            long offset = outAttrNamesFile.getFilePointer();
                            outAttrNamesFile.writeString(attrName);
                            return (int) offset;
                        } catch (IOException e1) {
                            throw new BlackLabRuntimeException(e1);
                        }
                    });
                    // Look up the attribute value offset, storing the attribute name if this is the first time we see it
                    long attrValueOffset = attrValueOffsets.computeIfAbsent(attrValue, k -> {
                        try {
                            long offset = outAttrValuesFile.getFilePointer();
                            outAttrValuesFile.writeString(attrValue);
                            return offset;
                        } catch (IOException e1) {
                            throw new BlackLabRuntimeException(e1);
                        }
                    });
                    currentTermAttributes.put(attrNameIndex, attrValueOffset);
                }
                // Look up this attribute set's offset, storing the attribute set if this is the first time we see it
                currentTermAttrSetOffset = idFromAttributeSet.computeIfAbsent(currentTermAttributes, k -> {
                    try {
                        long offset = outAttrSetsFile.getFilePointer();
                        outAttrSetsFile.writeInt(currentTermAttributes.size());
                        for (Entry<Integer, Long> e: currentTermAttributes.entrySet()) {
                            outAttrSetsFile.writeInt(e.getKey());    // attribute name id
                            outAttrSetsFile.writeLong(e.getValue()); // attribute value offset
                        }
                        return offset;
                    } catch (IOException e1) {
                        throw new BlackLabRuntimeException(e1);
                    }
                });
            }
        }

        @Override
        public void endTerm() {
            currentTermAttrSetOffset = -1;
        }

        @Override
        public void startDocument(int docId, int nOccurrences) {
            currentDocAttrSetOffsetPerRelationId = new ArrayList<>(1000);
        }

        @Override
        public void endDocument() throws IOException {
            // Write the offset in the relations file for this document to the docs file
            outDocsFile.writeLong(outRelationsFile.getFilePointer());

            // Write the attribute set offsets per relation to the relations file
            outRelationsFile.writeInt(currentDocAttrSetOffsetPerRelationId.size());
            for (long offset: currentDocAttrSetOffsetPerRelationId) {
                outRelationsFile.writeLong(offset);
            }
            currentDocAttrSetOffsetPerRelationId = null;
        }

        @Override
        public void termOccurrence(int position, BytesRef payload) throws IOException {
            if (payload == null)
                return;
            // Get the relation id from the payload and store the offset to this term's attribute value set.
            // We could also store other info about this occurrence here, such as info about an inline tag's parent and
            // children.
            ByteArrayDataInput dataInput = PayloadUtils.getDataInput(payload.bytes, false);
            int relationId = RelationInfo.getRelationId(dataInput);
            if (relationId >= 0) {
                int n = currentDocAttrSetOffsetPerRelationId.size();
                int extraElementsNeeded = relationId + 1 - n;
                if (extraElementsNeeded > 0) {
                    // Make sure we have enough space in the list
                    currentDocAttrSetOffsetPerRelationId.ensureCapacity(n);
                    for (int i = 0; i < extraElementsNeeded; i++)
                        currentDocAttrSetOffsetPerRelationId.add(0L);
                }
                currentDocAttrSetOffsetPerRelationId.set(relationId, currentTermAttrSetOffset);
            }
        }

        @Override
        public void finalize() throws IOException {


            // Write the footers to our files
            CodecUtil.writeFooter(outDocsFile);
            CodecUtil.writeFooter(outRelationsFile);
            CodecUtil.writeFooter(outAttrSetsFile);
            CodecUtil.writeFooter(outAttrNamesFile);
            CodecUtil.writeFooter(outAttrValuesFile);
        }

    }

    /**
     * Write our additions to the default postings (i.e. the forward index)
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
        List<TermOccurrenceAction> allActions = new ArrayList<>();
        try {
            allActions.add(new ForwardIndexWriter());
            allActions.add(new RelationInfoWriter());

            // Write our postings extension information

            // Process fields
            for (String luceneField: fields) { // for each field

                // Is this the relation annotation? Then we want to store relation info such as attribute values,
                // so we can look them up for individual relations matched.
                boolean storeRelationInfo = false;
                String[] nameComponents = AnnotatedFieldNameUtil.getNameComponents(luceneField);
                if (nameComponents.length > 1 && AnnotatedFieldNameUtil.isRelationAnnotation(nameComponents[1])) {
                    // Yes, store relation info.
                    storeRelationInfo = true;
                }

                // Should this field get a forward index?
                boolean storeForwardIndex = BlackLabIndexIntegrated.isForwardIndexField(
                        fieldInfos.fieldInfo(luceneField));

                // If we don't need to do any per-term processing, continue
                if (!storeForwardIndex && !storeRelationInfo)
                    continue;

                // Determine what actions to perform for this field
                List<TermOccurrenceAction> actions = new ArrayList<>();
                for (TermOccurrenceAction action: allActions) {
                    if (action.startField(fieldInfos.fieldInfo(luceneField)))
                        actions.add(action);
                }
                if (actions.isEmpty())
                    continue;

                // For each term in this field...
                PostingsEnum postingsEnum = null; // we'll reuse this for efficiency
                Terms terms = fields.terms(luceneField);
                TermsEnum termsEnum = terms.iterator();
                while (true) {
                    BytesRef term = termsEnum.next();
                    if (term == null)
                        break;

                    for (TermOccurrenceAction action: actions) action.startTerm(term);

                    // For each document containing this term...
                    postingsEnum = termsEnum.postings(postingsEnum, PostingsEnum.POSITIONS | PostingsEnum.PAYLOADS);
                    while (true) {
                        int docId = postingsEnum.nextDoc();
                        if (docId == DocIdSetIterator.NO_MORE_DOCS)
                            break;

                        // Go through each occurrence of term in this doc,
                        // gathering the positions where this term occurs as a "primary value"
                        // (the first value at this token position, which we will store in the
                        //  forward index). Also determine docLength.
                        int nOccurrences = postingsEnum.freq();
                        for (TermOccurrenceAction action: actions)
                            action.startDocument(docId, nOccurrences);
                        for (int i = 0; i < nOccurrences; i++) {
                            int position = postingsEnum.nextPosition();
                            BytesRef payload = postingsEnum.getPayload();
                            for (TermOccurrenceAction action: actions) action.termOccurrence(position, payload);
                        }
                        for (TermOccurrenceAction action: actions) action.endDocument();
                    }
                    for (TermOccurrenceAction action: actions) action.endTerm();
                }
                for (TermOccurrenceAction action: actions) action.endField();
            } // for each field

            for (TermOccurrenceAction action: allActions) action.finalize();
        } catch (IOException e) {
            throw new BlackLabRuntimeException(e);
        } finally {
            try {
                for (TermOccurrenceAction action: allActions) action.close();
            } catch (IOException e) {
                throw new BlackLabRuntimeException(e);
            }
        }
    }

    private int[] getDocumentContents(int docLength,
            IndexInput inTermVectorFile, TermVecFileOffsetPerTermId termPosOffsets)
            throws IOException {

        final int[] tokensInDoc = new int[docLength]; // reconstruct the document here

        // NOTE: sometimes docs won't have any values for a field, but we'll
        //   still write all NO_TERMs in this case. This is similar to sparse
        //   fields (e.g. the field that stores <p> <s> etc.) which also have a
        //   lot of NO_TERMs.
        Arrays.fill(tokensInDoc, NO_TERM);

        // For each term...
        for (Entry<Integer/*term ID*/, Long/*file offset*/> e: termPosOffsets.entrySet()) {
            int termId = e.getKey();
            inTermVectorFile.seek(e.getValue());
            int nOccurrences = inTermVectorFile.readInt();
            // For each occurrence...
            for (int i = 0; i < nOccurrences; i++) {
                int position = inTermVectorFile.readInt();
                tokensInDoc[position] = termId;
            }
        }

        return tokensInDoc;
    }

    /**
     * Write the tokens to the tokens file.
     *
     * Also records offset, length and encoding in the tokens index file.
     *
     * Chooses the most appropriate encoding for the tokens and records this choice in
     * the tokens index file.
     *
     * @param outTokensIndexFile token index file
     * @param outTokensFile      tokens file
     * @param tokensInDoc        tokens to write
     * @throws IOException       When failing to write
     */
    private void writeTokensInDoc(IndexOutput outTokensIndexFile, IndexOutput outTokensFile, int[] tokensInDoc) throws IOException {
        int max = 0, min = 0;
        boolean allTheSame = tokensInDoc.length > 0; // if no tokens, then not all the same.
        int last = -1;
        for (int token: tokensInDoc) {
            max = Math.max(max, token);
            min = Math.min(min, token);
            allTheSame = allTheSame && (last == -1 || last == token);
            last = token;
            if ((min < Short.MIN_VALUE || max > Short.MAX_VALUE) && !allTheSame) // stop if already at worst case (int per token + not all the same).
                break;
        }

        // determine codec
        TokensCodec tokensCodec = allTheSame ? TokensCodec.ALL_TOKENS_THE_SAME : TokensCodec.VALUE_PER_TOKEN;

        // determine parameter byte for codec.
        byte tokensCodecParameter = 0;
        switch (tokensCodec) {
            case ALL_TOKENS_THE_SAME: tokensCodecParameter = 0; break;
            case VALUE_PER_TOKEN: {
                if (min >= Byte.MIN_VALUE && max <= Byte.MAX_VALUE) tokensCodecParameter = VALUE_PER_TOKEN_PARAMETER.BYTE.code;
                else if (min >= Short.MIN_VALUE && max <= Short.MAX_VALUE) tokensCodecParameter = VALUE_PER_TOKEN_PARAMETER.SHORT.code;
                else if (min >= ThreeByteInt.MIN_VALUE && max <= ThreeByteInt.MAX_VALUE) tokensCodecParameter = VALUE_PER_TOKEN_PARAMETER.THREE_BYTES.code;
                else tokensCodecParameter = VALUE_PER_TOKEN_PARAMETER.INT.code;
                break;
            }
            default: throw new NotImplementedException("Parameter byte determination for tokens codec " + tokensCodec + " not implemented.");
        }

        // Write offset in the tokens file, doc length in tokens and tokens codec used
        outTokensIndexFile.writeLong(outTokensFile.getFilePointer());
        outTokensIndexFile.writeInt(tokensInDoc.length);
        outTokensIndexFile.writeByte(tokensCodec.code);
        outTokensIndexFile.writeByte(tokensCodecParameter);

        if (tokensInDoc.length == 0) {
            return; // done.
        }

        // Write the tokens
        switch (tokensCodec) {
        case VALUE_PER_TOKEN: 
            switch (VALUE_PER_TOKEN_PARAMETER.fromCode(tokensCodecParameter)) {
                case BYTE: 
                    for (int token: tokensInDoc) {
                        outTokensFile.writeByte((byte) token);
                    }
                    break;
                case SHORT: 
                    for (int token: tokensInDoc) {
                        outTokensFile.writeShort((short) token);
                    }
                    break;
                case THREE_BYTES:
                    for (int token : tokensInDoc) {
                        ThreeByteInt.write((b) -> outTokensFile.writeByte(b), token);
                    }
                    break;
                case INT:
                    for (int token: tokensInDoc) {
                        outTokensFile.writeInt((int) token);
                    }
                    break;
                    default: throw new NotImplementedException("Handling for tokens codec " + tokensCodec + " with parameter " + tokensCodecParameter + " not implemented.");
                }
                break;
        case ALL_TOKENS_THE_SAME:
            outTokensFile.writeInt(tokensInDoc[0]);
            break;
        }
    }

    private IndexOutput createOutput(String ext) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, ext);
        IndexOutput output = state.directory.createOutput(fileName, state.context);

        // Write standard header, with the codec name and version, segment info.
        // Also write the delegate codec name (Lucene's default codec).
        CodecUtil.writeIndexHeader(output, BlackLab40PostingsFormat.NAME, BlackLab40PostingsFormat.VERSION_CURRENT,
                state.segmentInfo.getId(), state.segmentSuffix);
        output.writeString(delegatePostingsFormatName);

        return output;
    }

    @SuppressWarnings("SameParameterValue")
    private IndexInput openInput(String ext) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, ext);
        IndexInput input = state.directory.openInput(fileName, state.context);

        // Read and check standard header, with codec name and version and segment info.
        // Also check the delegate codec name (should be the expected version of Lucene's codec).
        CodecUtil.checkIndexHeader(input, BlackLab40PostingsFormat.NAME, BlackLab40PostingsFormat.VERSION_START,
                BlackLab40PostingsFormat.VERSION_CURRENT, state.segmentInfo.getId(), state.segmentSuffix);
        String delegatePFN = input.readString();
        if (!delegatePostingsFormatName.equals(delegatePFN))
            throw new IOException("Segment file " + fileName +
                    " contains wrong delegate postings format name: " + delegatePFN +
                    " (expected " + delegatePostingsFormatName + ")");

        return input;
    }

    @SuppressWarnings("SameParameterValue")
    private void deleteIndexFile(String ext) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, ext);
        state.directory.deleteFile(fileName);
    }

    @Override
    public void close() throws IOException {
        delegateFieldsConsumer.close();
    }
    /**
     * Given a list of terms, return the indices to sort them.
     * E.G: getTermSortOrder(['b','c','a']) --> [2, 0, 1]
     */
    private static int[] getTermSortOrder(List<String> terms, Collator coll) {
        int[] ret = new int[terms.size()];
        for (int i = 0; i < ret.length; ++i) ret[i] = i;

        // Collator.compare() is synchronized, so precomputing the collation keys speeds things up
        CollationKey[] ck = new CollationKey[terms.size()];
        for (int i = 0; i < ck.length; ++i)
            ck[i] = coll.getCollationKey(terms.get(i));

        IntArrays.parallelQuickSort(ret, (a, b) -> ck[a].compareTo(ck[b]));
        return ret;
    }

    /**
     * Invert the given array so the values become the indexes and vice versa.
     *
     * @param array array to invert
     * @return inverted array
     */
    private static int[] invert(List<String> terms, int[] array, Collator collator) {
        int[] result = new int[array.length];
        int prevSortPosition = -1;
        int prevTermId = -1;
        for (int i = 0; i < array.length; i++) {
            int termId = array[i];
            int sortPosition = i;
            if (prevTermId >= 0 && collator.equals(terms.get(prevTermId), terms.get(termId))) {
                // Keep the same sort position because the terms are the same
                sortPosition = prevSortPosition;
            } else {
                // Remember the sort position in case the next term is identical
                prevSortPosition = sortPosition;
            }
            result[termId] = sortPosition;
            prevTermId = termId;
        }
        return result;
    }
}
