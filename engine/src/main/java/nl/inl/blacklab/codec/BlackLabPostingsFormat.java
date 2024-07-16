package nl.inl.blacklab.codec;

import org.apache.lucene.codecs.PostingsFormat;

public abstract class BlackLabPostingsFormat extends PostingsFormat {
    public BlackLabPostingsFormat(String name) {
        super(name);
    }
}
