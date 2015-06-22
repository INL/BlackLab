package nl.inl.blacklab.analysis;

import java.io.Reader;

import org.apache.lucene.analysis.util.CharTokenizer;
import org.apache.lucene.util.AttributeSource;
import org.apache.lucene.util.Version;

/**
 * A tokenizer that doesn't tokenize (returns the whole
 * field value as one token)
 */
public class BLNonTokenizer extends CharTokenizer {

	public BLNonTokenizer(AttributeFactory factory, Reader input) {
		super(Version.LUCENE_42, factory, input);
	}

	public BLNonTokenizer(AttributeSource source, Reader input) {
		super(Version.LUCENE_42, source, input);
	}

	public BLNonTokenizer(Reader input) {
		super(Version.LUCENE_42, input);
	}

	@Override
	protected boolean isTokenChar(int c) {
		return true;
	}

}
