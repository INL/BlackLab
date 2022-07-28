package nl.inl.blacklab.codec;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.codecs.*;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;

/**
 * The custom codec that BlackLab uses.
 *
 * This is a customization of Lucene's way of storing information in the index,
 * to accomodate our forward index and (optional) content store.
 *
 * This functions as an adapter that wraps a delegate
 * codec (usually the default Solr codec) and simply proxies
 * most requests to that codec. It will handle specific requests
 * itself, though, in this case the {@link #postingsFormat()} method
 * that returns the {@link BLCodecPostingsFormat} object responsible for
 * saving/loading postings data (the actual inverted index, with
 * frequencies, offsets, payloads, etc.).
 *
 * This is referenced in Solr schema, e.g.:
 * <pre>
 * &lt;fieldType name="blacklab_text_example_test" class="solr.TextField" postingsFormat="BLCodec"&gt;
 * </pre>
 *
 * This class is declared in META-INF/services/org.apache.lucene.codecs.Codec
 *
 * Adapted from <a href="https://github.com/meertensinstituut/mtas/">MTAS</a>.
 */
public class BLCodec extends Codec {

    protected static final Logger logger = LogManager.getLogger(BLCodec.class);

    /** Our codec's name. */
    public static final String CODEC_NAME = "BLCodec";

    /** If we can't find the postings format the delegate uses, use this one. */
    public static final String LUCENE_DEFAULT_POSTINGS_FORMAT_NAME = "Lucene84";

    /** The codec we're basing this codec on. */
    Codec delegate;

    // Needed for SPI
    @SuppressWarnings("unused")
    public BLCodec() {
        super(CODEC_NAME);
        delegate = null;
    }

    public BLCodec(String name, Codec delegate) {
        super(name);
        this.delegate = delegate;
    }

    /** If we don't have a delegate yet, use the default Codec. */
    private void initDelegate() {
        if (delegate == null) {
            delegate = Codec.getDefault();
        }
    }

    @Override
    public PostingsFormat postingsFormat() {
        initDelegate();
        if (delegate.postingsFormat() instanceof PerFieldPostingsFormat) {
            Codec defaultCodec = Codec.getDefault();
            PostingsFormat defaultPostingsFormat = defaultCodec.postingsFormat();
            if (defaultPostingsFormat instanceof PerFieldPostingsFormat) {
                defaultPostingsFormat = ((PerFieldPostingsFormat) defaultPostingsFormat)
                        .getPostingsFormatForField("");
                if ((defaultPostingsFormat == null)
                        || (defaultPostingsFormat instanceof PerFieldPostingsFormat)) {
                    // fallback option
                    defaultPostingsFormat = PostingsFormat.forName(LUCENE_DEFAULT_POSTINGS_FORMAT_NAME);
                }
            }
            return new BLCodecPostingsFormat(defaultPostingsFormat);
        }
        return new BLCodecPostingsFormat(delegate.postingsFormat());
    }

    @Override
    public DocValuesFormat docValuesFormat() {
        initDelegate();
        return delegate.docValuesFormat();
    }

    @Override
    public StoredFieldsFormat storedFieldsFormat() {
        initDelegate();
        return delegate.storedFieldsFormat();
    }

    @Override
    public TermVectorsFormat termVectorsFormat() {
        initDelegate();
        return delegate.termVectorsFormat();
    }

    @Override
    public FieldInfosFormat fieldInfosFormat() {
        initDelegate();
        return delegate.fieldInfosFormat();
    }

    @Override
    public SegmentInfoFormat segmentInfoFormat() {
        initDelegate();
        return delegate.segmentInfoFormat();
    }

    @Override
    public NormsFormat normsFormat() {
        initDelegate();
        return delegate.normsFormat();
    }

    @Override
    public LiveDocsFormat liveDocsFormat() {
        initDelegate();
        return delegate.liveDocsFormat();
    }

    @Override
    public CompoundFormat compoundFormat() {
        initDelegate();
        return delegate.compoundFormat();
    }

    @Override
    public PointsFormat pointsFormat() {
        initDelegate();
        return delegate.pointsFormat();
    }

}
