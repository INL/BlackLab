package nl.inl.blacklab.codec;

import org.apache.lucene.codecs.Codec;

public abstract class BlackLabCodec extends Codec {

    protected BlackLabCodec(String name) {
        super(name);
    }
}
