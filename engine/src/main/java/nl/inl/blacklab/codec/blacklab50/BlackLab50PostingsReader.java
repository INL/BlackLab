package nl.inl.blacklab.codec.blacklab50;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;

import net.jcip.annotations.ThreadSafe;
import nl.inl.blacklab.codec.BLTerms;
import nl.inl.blacklab.codec.BlackLabPostingsReader;
import nl.inl.blacklab.codec.BlackLabStoredFieldsReader;
import nl.inl.blacklab.codec.SegmentForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndexSegmentReader;

/**
 * Adds forward index reading to default FieldsProducer.
 *
 * Each index segment has an instance of BLFieldsProducer.
 * It opens the custom segment files for the forward index
 * (and any other custom files).
 *
 * Delegates all other methods to the default FieldsProducer.
 *
 * Adapted from <a href="https://github.com/meertensinstituut/mtas/">MTAS</a>.
 *
 * Thread-safe. It does store IndexInput which contains state, but those
 * are cloned whenever a thread needs to use them.
 */
@ThreadSafe
public class BlackLab50PostingsReader extends BlackLabPostingsReader {

    protected static final Logger logger = LogManager.getLogger(BlackLab50PostingsReader.class);

    private final SegmentReadState state;

    /** Name of PF we delegate to (the one from Lucene) */
    private String delegateFormatName;

    /** The delegate whose functionality we're extending */
    private final FieldsProducer delegateFieldsProducer;

    /** The forward index */
    private final SegmentForwardIndex forwardIndex;

    /** Terms object for each field */
    private final Map<String, BLTerms> termsPerField = new HashMap<>();

    public BlackLab50PostingsReader(SegmentReadState state) throws IOException {
        this.state = state;

        // NOTE: opening the forward index calls openInputFile, which reads
        //       delegatePostingsFormatName, so this must be done first.
        forwardIndex = new SegmentForwardIndex(this);
        if (delegateFormatName == null)
            throw new IllegalStateException("Opening the segment FI should have set the delegate format name");

        PostingsFormat delegatePostingsFormat = PostingsFormat.forName(delegateFormatName);
        delegateFieldsProducer = delegatePostingsFormat.fieldsProducer(state);
    }

    @Override
    public Iterator<String> iterator() {
        return delegateFieldsProducer.iterator();
    }

    @Override
    public void close() throws IOException {
        forwardIndex.close();
        delegateFieldsProducer.close();
    }

    @Override
    public BLTerms terms(String field) throws IOException {
        BLTerms terms;
        synchronized (termsPerField) {
            terms = termsPerField.get(field);
            if (terms == null) {
                Terms delegateTerms = delegateFieldsProducer.terms(field);

                terms = delegateTerms == null ? null : new BLTerms(delegateTerms, this);
                termsPerField.put(field, terms);
            }
        }
        return terms;
    }

    @Override
    public BlackLabStoredFieldsReader getStoredFieldsReader() {
        try {
            BlackLab50Codec codec = (BlackLab50Codec) state.segmentInfo.getCodec();
            return codec.storedFieldsFormat().fieldsReader(
                    state.directory, state.segmentInfo, state.fieldInfos, state.context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int size() {
        return delegateFieldsProducer.size();
    }

    @Override
    public void checkIntegrity() throws IOException {
        delegateFieldsProducer.checkIntegrity();

        // TODO: check integrity of our own (FI) files?
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(delegate=" + delegateFieldsProducer + ")";
    }

    public IndexInput openInputCorrectEndian(Directory directory, String fileName, IOContext ioContext) throws IOException {
        return directory.openInput(fileName, ioContext);
    }

    /**
     * Open a custom file for reading and check the header.
     *
     * @param extension extension of the file to open (should be one of the prefixed constants from BlacklabPostingsFormat)
     * @return handle to the opened segment file
     */
    public IndexInput openIndexFile(String extension) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, extension);
        // NOTE: we have to deal with Lucene 9's switch to little-endian.
        //IndexInput input = state.directory.openInput(fileName, state.context);
        IndexInput input = openInputCorrectEndian(state.directory, fileName, state.context);
        try {
            // Check index header
            CodecUtil.checkIndexHeader(input, BlackLab50PostingsFormat.NAME, BlackLab50PostingsFormat.VERSION_START,
                    BlackLab50PostingsFormat.VERSION_CURRENT, state.segmentInfo.getId(), state.segmentSuffix);

            // Check delegate format name
            String delegateFN = input.readString();
            if (delegateFormatName == null)
                delegateFormatName = delegateFN;
            if (!delegateFormatName.equals(delegateFN))
                throw new IOException("Segment file " + fileName +
                        " contains wrong delegate format name: " + delegateFN +
                        " (expected " + delegateFormatName + ")");

            return input;
        } catch (Exception e) {
            input.close();
            throw e;
        }
    }

    /**
     * Create a forward index reader for this segment.
     *
     * The returned reader is not threadsafe and shouldn't be stored.
     * A single thread may use it for reading from this segment. It
     * can then be discarded.
     *
     * @return forward index segment reader
     */
    public ForwardIndexSegmentReader forwardIndex() {
        return forwardIndex.reader();
    }

//    /**
//     * Get the BlackLab50PostingsReader for the given leafreader.
//     *
//     * @param lrc leafreader to get the BlackLab50PostingsReader for
//     * @return BlackLab50PostingsReader for this leafreader
//     */
//    public static BlackLab50PostingsReader get(LeafReaderContext lrc) {
//        return (BlackLab50PostingsReader) BLTerms.getTerms(lrc).getFieldsProducer();
//    }

}