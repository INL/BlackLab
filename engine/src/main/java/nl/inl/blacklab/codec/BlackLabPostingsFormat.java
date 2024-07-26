package nl.inl.blacklab.codec;

import org.apache.lucene.codecs.PostingsFormat;

public abstract class BlackLabPostingsFormat extends PostingsFormat {
    /** Every file extension will be prefixed with this to indicate it is part of the forward index. */
    private static final String EXT_PREFIX = "blfi.";
    /** Extension for the fields file. This stores the annotated field name and the offset
     in the term index file where the term offsets ares stored.*/
    public static final String FIELDS_EXT = EXT_PREFIX + "fields";
    /** Extension for the term index file, that stores the offset in the terms file where
     the term strings start for each term (in each annotated field). */
    public static final String TERMINDEX_EXT = EXT_PREFIX + "termindex";
    /** Extension for the terms file, where the term strings are stored. */
    public static final String TERMS_EXT = EXT_PREFIX + "terms";
    /** Extension for the terms order file, where indices for different sorts of the term strings are stored.
     * pos2IDInsensitive, id2PosInsensitive, pos2IDSensitive, id2PosSensitive */
    public static final String TERMORDER_EXT = EXT_PREFIX + "termorder";
    /** Extension for the tokens index file, that stores the offsets in the tokens file
     where the tokens for each document are stored. */
    public static final String TOKENS_INDEX_EXT = EXT_PREFIX + "tokensindex";
    /** Extension for the tokens file, where a term id is stored for each position in each document. */
    public static final String TOKENS_EXT = EXT_PREFIX + "tokens";
    /** Extension for the temporary term vector file that will be converted later.
     * The term vector file contains the occurrences for each term in each doc (and each annotated field)
     */
    public static final String TERMVEC_TMP_EXT = EXT_PREFIX + "termvec.tmp";

    public BlackLabPostingsFormat(String name) {
        super(name);
    }
}
