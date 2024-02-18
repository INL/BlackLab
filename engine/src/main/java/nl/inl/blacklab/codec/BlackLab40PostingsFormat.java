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

    /** Every forward index file extension will be prefixed with this to indicate it is part of the forward index. */
    private static final String EXT_FI_PREFIX = "blfi.";

    /** Extension for the fields file. This stores the annotated field name and the offset
        in the term index file where the term offsets ares stored.*/
    public static final String FI_FIELDS_EXT = EXT_FI_PREFIX + "fields";

    /** Extension for the term index file, that stores the offset in the terms file where
        the term strings start for each term (in each annotated field). */
    public static final String FI_TERMINDEX_EXT = EXT_FI_PREFIX + "termindex";

    /** Extension for the terms file, where the term strings are stored. */
    public static final String FI_TERMS_EXT = EXT_FI_PREFIX + "terms";

    /** Extension for the terms order file, where indices for different sorts of the term strings are stored.
     * pos2IDInsensitive, id2PosInsensitive, pos2IDSensitive, id2PosSensitive */
    public static final String FI_TERMORDER_EXT = EXT_FI_PREFIX + "termorder";

    /** Extension for the tokens index file, that stores the offsets in the tokens file
        where the tokens for each document are stored. */
    static final String FI_TOKENS_INDEX_EXT = EXT_FI_PREFIX + "tokensindex";

    /** Extension for the tokens file, where a term id is stored for each position in each document. */
    static final String FI_TOKENS_EXT = EXT_FI_PREFIX + "tokens";

    /** Extension for the temporary term vector file that will be converted later.
     * The term vector file contains the occurrences for each term in each doc (and each annotated field)
     */
    static final String FI_TERMVEC_TMP_EXT = EXT_FI_PREFIX + "termvec.tmp";

    /** Every relation info file extension will be prefixed with this to indicate it is part of the relation info. */
    private static final String EXT_RELINFO_PREFIX = "blri.";

    static final String RI_DOCS_EXT = EXT_RELINFO_PREFIX + "docs";

    static final String RI_RELATIONS_EXT = EXT_RELINFO_PREFIX + "relations";

    static final String RI_ATTR_SETS_EXT = EXT_RELINFO_PREFIX + "attrsets";

    static final String RI_ATTR_NAMES_EXT = EXT_RELINFO_PREFIX + "attrnames";

    static final String RI_ATTR_VALUES_EXT = EXT_RELINFO_PREFIX + "attrvalues";

    /** Extension for the temporary relations file that will be converted later.
     * The temporary file contains the attribute set id for each unique relation+attributes in each doc (and each annotated field)
     */
    static final String RI_RELATIONS_TMP_EXT = EXT_RELINFO_PREFIX + "relations.tmp";

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
