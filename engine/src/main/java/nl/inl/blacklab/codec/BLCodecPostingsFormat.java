package nl.inl.blacklab.codec;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FieldsConsumer;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;

/**
 * The custom postings format that BlackLab uses.
 *
 * This is an extension of Lucene's "postings" information
 * (e.g. term frequence, positions, offsets, etc.) in the index, to accomodate our forward
 * index and (optional) content store.

 * A {@link PostingsFormat} is the object responsible for
 * saving/loading postings data (the actual inverted index, with
 * frequencies, offsets, payloads, etc.).
 *
 * This functions as an adapter that wraps a delegate
 * PostingsFormat (usually the default Solr PostingsFormat)
 * which is used to create adapted versions of e.g. FieldsConsumer.
 *
 * This class is declared in META-INF/services/org.apache.lucene.codecs.PostingsFormat
 *
 * Adapted from <a href="https://github.com/meertensinstituut/mtas/">MTAS</a>.
 */
public class BLCodecPostingsFormat extends PostingsFormat {

    protected static final Logger logger = LogManager.getLogger(BLCodecPostingsFormat.class);

    /** Oldest postings version still supported */
    public static final int VERSION_START = 1;

    /** Current postings version */
    public static final int VERSION_CURRENT = 1;

    /** Name of the PostingsFormat we delegate most requests to. */
    private String delegateCodecName = null;

    /** The PostingsFormat we're wrapping and we delegate most requests to. */
    private PostingsFormat delegatePostingsFormat = null;

    public BLCodecPostingsFormat() {
        this(BLCodec.CODEC_NAME);
    }

    public BLCodecPostingsFormat(PostingsFormat delegate) {
        super(BLCodec.CODEC_NAME);
        delegateCodecName = delegate.getName();
        delegatePostingsFormat = delegate;
//        // preload to prevent NoClassDefFoundErrors
//        try {
//            Class.forName("mtas.codec.payload.BLPayloadDecoder");
//            Class.forName("mtas.codec.payload.BLBitInputStream");
//            Class.forName("mtas.analysis.token.BLPosition");
//            Class.forName("mtas.analysis.token.BLOffset");
//            Class.forName("mtas.codec.tree.BLRBTree");
//            Class.forName("mtas.codec.BLTerms");
//            Class.forName("mtas.codec.util.CodecInfo");
//            Class.forName("mtas.codec.tree.BLTreeNodeId");
//        } catch (ClassNotFoundException e) {
//            log.error(e);
//        }
    }

    public BLCodecPostingsFormat(String codecName) {
        super(codecName);
        delegateCodecName = codecName;
        delegatePostingsFormat = null;
//        // preload to prevent NoClassDefFoundErrors
//        try {
//            Class.forName("mtas.codec.payload.BLPayloadDecoder");
//            Class.forName("mtas.codec.payload.BLBitInputStream");
//            Class.forName("mtas.analysis.token.BLPosition");
//            Class.forName("mtas.analysis.token.BLOffset");
//            Class.forName("mtas.codec.tree.BLRBTree");
//            Class.forName("mtas.codec.BLTerms");
//            Class.forName("mtas.codec.util.CodecInfo");
//            Class.forName("mtas.codec.tree.BLTreeNodeId");
//        } catch (ClassNotFoundException e) {
//            log.error(e);
//        }
    }

    @Override
    public final FieldsProducer fieldsProducer(SegmentReadState state)
            throws IOException {
        return new BLFieldsProducer(state, getName());
    }

    @Override
    public final FieldsConsumer fieldsConsumer(SegmentWriteState state)
            throws IOException {
        if (delegatePostingsFormat != null) {
            return new BLFieldsConsumer(
                    delegatePostingsFormat.fieldsConsumer(state), state, getName(),
                    delegatePostingsFormat.getName());
        }
        PostingsFormat pf = Codec.forName(delegateCodecName).postingsFormat();
        return pf.fieldsConsumer(state);
    }

}
