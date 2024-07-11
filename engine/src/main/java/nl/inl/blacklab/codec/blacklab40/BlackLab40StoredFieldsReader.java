package nl.inl.blacklab.codec.blacklab40;

import java.io.IOException;

import org.apache.lucene.backward_codecs.store.EndiannessReverserUtil;
import org.apache.lucene.codecs.StoredFieldsReader;
import org.apache.lucene.index.FieldInfos;
import org.apache.lucene.index.SegmentInfo;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;

import nl.inl.blacklab.codec.blacklab50.BlackLab50StoredFieldsReader;

public class BlackLab40StoredFieldsReader extends BlackLab50StoredFieldsReader {

    public BlackLab40StoredFieldsReader(Directory directory, SegmentInfo segmentInfo,
            IOContext ioContext, FieldInfos fieldInfos,
            StoredFieldsReader delegate, String delegateFormatName) throws IOException {
        super(directory, segmentInfo, ioContext, fieldInfos, delegate, delegateFormatName);
    }

    @Override
    public IndexInput openInputCorrectEndian(Directory directory, String fileName, IOContext ioContext) throws IOException {
        return EndiannessReverserUtil.openInput(directory, fileName, ioContext);
    }
}
