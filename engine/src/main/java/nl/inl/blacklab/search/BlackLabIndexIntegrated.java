package nl.inl.blacklab.search;

import java.io.File;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.index.IndexWriterConfig;

import nl.inl.blacklab.codec.BLCodec;
import nl.inl.blacklab.exceptions.ErrorOpeningIndex;
import nl.inl.blacklab.forwardindex.ForwardIndex;
import nl.inl.blacklab.forwardindex.ForwardIndexIntegrated;
import nl.inl.blacklab.indexers.config.ConfigInputFormat;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessor;
import nl.inl.blacklab.search.fimatch.ForwardIndexAccessorIntegrated;
import nl.inl.blacklab.search.indexmetadata.AnnotatedField;

/**
 * A BlackLab index with all files included in the Lucene index.
 */
public class BlackLabIndexIntegrated extends BlackLabIndexAbstract {

    BlackLabIndexIntegrated(BlackLabEngine blackLab, File indexDir, boolean indexMode, boolean createNewIndex,
            ConfigInputFormat config) throws ErrorOpeningIndex {
        super(blackLab, indexDir, indexMode, createNewIndex, config);
    }

    BlackLabIndexIntegrated(BlackLabEngine blackLab, File indexDir, boolean indexMode, boolean createNewIndex,
            File indexTemplateFile) throws ErrorOpeningIndex {
        super(blackLab, indexDir, indexMode, createNewIndex, indexTemplateFile);
    }

    public ForwardIndex createForwardIndex(AnnotatedField field) {
        return new ForwardIndexIntegrated(this, field);
    }

    @Override
    protected void customizeIndexWriterConfig(IndexWriterConfig config) {
        config.setCodec(new BLCodec(BLCodec.CODEC_NAME, Codec.getDefault())); // our own custom codec (extended from Lucene)
        config.setUseCompoundFile(false); // @@@ TEST
    }

    @Override
    public ForwardIndexAccessor forwardIndexAccessor(String searchField) {
        return new ForwardIndexAccessorIntegrated(this, annotatedField(searchField));
    }
}
