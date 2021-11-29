package nl.inl.blacklab.codec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.codecs.CodecUtil;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.IndexFormatTooOldException;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.Terms;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.util.Accountable;
import org.apache.lucene.util.Accountables;

/**
 * BlackLab FieldsProducer: opens our custom index files to access
 * our forward index, (optional) content store, etc.
 *
 * Adapted from <a href="https://github.com/meertensinstituut/mtas/">MTAS</a>.
 */
public class BLFieldsProducer extends FieldsProducer {

    protected static final Logger logger = LogManager.getLogger(BLFieldsProducer.class);

    /** The delegate whose functionality we're extending */
    private FieldsProducer delegateFieldsProducer;

    /** Index format version */
    @SuppressWarnings("unused")
    private int version;

    public BLFieldsProducer(SegmentReadState state, String name)
            throws IOException {
        String postingsFormatName = "Lucene50";
        version = BLCodecPostingsFormat.VERSION_CURRENT;

        // Load the delegate postingsFormatName from this file
        this.delegateFieldsProducer = PostingsFormat.forName(postingsFormatName).fieldsProducer(state);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.lucene.index.Fields#iterator()
     */
    @Override
    public Iterator<String> iterator() {
        return delegateFieldsProducer.iterator();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.lucene.codecs.FieldsProducer#close()
     */
    @Override
    public void close() throws IOException {
        delegateFieldsProducer.close();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.lucene.index.Fields#terms(java.lang.String)
     */
    @Override
    public Terms terms(String field) throws IOException {
        return delegateFieldsProducer.terms(field);
        //return new BLTerms(delegateFieldsProducer.terms(field), indexInputList, indexInputOffsetList, version);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.lucene.index.Fields#size()
     */
    @Override
    public int size() {
        return delegateFieldsProducer.size();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.lucene.util.Accountable#ramBytesUsed()
     */
    @Override
    public long ramBytesUsed() {
        // return BASE_RAM_BYTES_USED + delegateFieldsProducer.ramBytesUsed();
        return 3 * delegateFieldsProducer.ramBytesUsed();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.lucene.util.Accountable#getChildResources()
     */
    @Override
    public Collection<Accountable> getChildResources() {
        List<Accountable> resources = new ArrayList<>();
        if (delegateFieldsProducer != null) {
            resources.add(Accountables.namedAccountable("delegate", delegateFieldsProducer));
        }
        return Collections.unmodifiableList(resources);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.apache.lucene.codecs.FieldsProducer#checkIntegrity()
     */
    @Override
    public void checkIntegrity() throws IOException {
        delegateFieldsProducer.checkIntegrity();
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return getClass().getSimpleName() + "(delegate=" + delegateFieldsProducer + ")";
    }

    private static IndexInput openIndexFile(SegmentReadState state, String name, String extension,
            Integer minimum, Integer maximum) throws IOException {
        String fileName = IndexFileNames.segmentFileName(state.segmentInfo.name, state.segmentSuffix, extension);
        IndexInput object = state.directory.openInput(fileName, state.context);
        int minVersion = (minimum == null) ? BLCodecPostingsFormat.VERSION_START : minimum.intValue();
        int maxVersion = (maximum == null) ? BLCodecPostingsFormat.VERSION_CURRENT : maximum.intValue();
        try {
            CodecUtil.checkIndexHeader(object, name, minVersion, maxVersion, state.segmentInfo.getId(), state.segmentSuffix);
        } catch (IndexFormatTooOldException e) {
            object.close();
            logger.debug(e);
            throw new IndexFormatTooOldException(e.getMessage(), e.getVersion(), e.getMinVersion(), e.getMaxVersion());
        }
        return object;
    }

    @SuppressWarnings("unused")
    private static IndexInput openIndexFile(SegmentReadState state, String name, String extension) throws IOException {
        return openIndexFile(state, name, extension, null, null);
    }

}
