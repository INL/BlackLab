package nl.inl.blacklab.codec;

import java.io.IOException;

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
public class BlackLab40PostingsFormat extends PostingsFormat {

    /** Name of this codec. Written to the files and checked on reading. */
    static final String NAME = "BlackLab40Postings";

    /** Oldest version still supported */
    static final int VERSION_START = 1;

    /** Current version */
    static final int VERSION_CURRENT = 1;

    /** Every file extension will be prefixed with this to indicate it is part of the forward index. */
    public static final String EXT_PREFIX = "blfi.";

    /** Extension for the fields file. This stores the annotated field name and the offset
        in the term index file where the term offsets ares stored.*/
    static final String FIELDS_EXT = "fields";

    /** Extension for the term index file, that stores the offset in the terms file where
        the term strings start for each term (in each annotated field). */
    static final String TERMINDEX_EXT = "termindex";

    /** Extension for the terms file, where the term strings are stored. */
    static final String TERMS_EXT = "terms";

    /** Extension for the terms order file, where indices for different sorts of the term strings are stored.
     * pos2IDInsensitive, id2PosInsensitive, pos2IDSensitive, id2PosSensitive */
    static final String TERMORDER_EXT = "termorder";

    /** Extension for the tokens index file, that stores the offsets in the tokens file
        where the tokens for each document are stored. */
    static final String TOKENS_INDEX_EXT = "tokensindex";

    /** Extension for the tokens file, where a term id is stored for each position in each document. */
    static final String TOKENS_EXT = "tokens";

    /** Extension for the temporary term vector file that will be converted later.
     * The term vector file contains the occurrences for each term in each doc (and each annotated field)
     */
    static final String TERMVEC_TMP_EXT = "termvec.tmp";

    /** The PostingsFormat we're wrapping and we delegate most requests to. */
    private final PostingsFormat delegatePostingsFormat;

    // Used when opening index (see corresponding PostingsReader constructor)
    @SuppressWarnings("unused")
    public BlackLab40PostingsFormat() {
        super(NAME);
        BlackLab40PostingsFormat pf = ((BlackLab40Codec)Codec.forName(BlackLab40Codec.NAME)).postingsFormat();
        delegatePostingsFormat = pf.delegatePostingsFormat;
    }

    public BlackLab40PostingsFormat(PostingsFormat delegate) {
        super(NAME);
        delegatePostingsFormat = delegate;
    }

    @Override
    public final FieldsProducer fieldsProducer(SegmentReadState state) throws IOException {
        return new BlackLab40PostingsReader(state);
    }

    @Override
    public final FieldsConsumer fieldsConsumer(SegmentWriteState state) throws IOException {
        return new BlackLab40PostingsWriter(delegatePostingsFormat.fieldsConsumer(state), state,
                delegatePostingsFormat.getName());
    }

}
