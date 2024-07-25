package org.ivdnt.blacklab.solr;

import org.apache.lucene.codecs.Codec;
import org.apache.solr.core.CodecFactory;

import nl.inl.blacklab.codec.BlackLab40Codec;

public class BLCodecFactory extends CodecFactory {

    @Override
    public Codec getCodec() {
        System.out.println("creating codec");
        return new BlackLab40Codec();
    }
}
