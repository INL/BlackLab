package nl.inl.blacklab.codec;

import org.apache.lucene.codecs.StoredFieldsFormat;

public abstract class BlackLabStoredFieldsFormat extends StoredFieldsFormat {

    /** Every file extension will be prefixed with this to indicate it is part of the content store. */
    private static final String EXT_PREFIX = "blcs.";
    /** Extension for the fields file, that stores block size and Lucene fields with a CS. */
    public static final String FIELDS_EXT = EXT_PREFIX + "fields";
    /** Extension for the docindex file. */
    public static final String DOCINDEX_EXT = EXT_PREFIX + "docindex";

    /** Extension for the valueindex file. */
    public static final String VALUEINDEX_EXT = EXT_PREFIX + "valueindex";

    /** Extension for the blockindex file. */
    public static final String BLOCKINDEX_EXT = EXT_PREFIX + "blockindex";

    /** Extension for the blocks file. */
    public static final String BLOCKS_EXT = EXT_PREFIX + "blocks";
}
