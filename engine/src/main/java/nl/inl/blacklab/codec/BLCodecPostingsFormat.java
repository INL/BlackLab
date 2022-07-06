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

    /** Extension for the fields file. This stores the annotated field name and the offset
        in the term index file where the term offsets ares stored.*/
    static final String FIELDS_EXT = "fields";

    /** Extension for the term index file, that stores the offset in the terms file where
        the term strings start for each term (in each annotated field). */
    static final String TERMINDEX_EXT = "termindex";

    /** Extension for the terms file, where the term strings are stored. */
    static final String TERMS_EXT = "terms";

    /** Extension for the tokens index file, that stores the offsets in the tokens file
        where the tokens for each document are stored. */
    static final String TOKENS_INDEX_EXT = "tokensindex";

    /** Extension for the tokens file, where a term id is stored for each position in each document. */
    static final String TOKENS_EXT = "tokens";

    /** Extension for the temporary term vector file that will be converted later.
     * The term vector file contains the occurrences for each term in each doc (and each annotated field)
     */
    static final String TERMVEC_TMP_EXT = "termvec.tmp";

    /** Name of the PostingsFormat we delegate most requests to. */
    private final String delegateCodecName;

    /** The PostingsFormat we're wrapping and we delegate most requests to. */
    private final PostingsFormat delegatePostingsFormat;

    public BLCodecPostingsFormat() {
        this(BLCodec.CODEC_NAME);
    }

    public BLCodecPostingsFormat(PostingsFormat delegate) {
        super(BLCodec.CODEC_NAME);
        delegateCodecName = delegate.getName();
        delegatePostingsFormat = delegate;
    }

    public BLCodecPostingsFormat(String codecName) {
        super(codecName);
        delegateCodecName = codecName;
        delegatePostingsFormat = null;
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
