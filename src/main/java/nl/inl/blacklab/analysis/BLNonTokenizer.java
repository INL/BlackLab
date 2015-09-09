package nl.inl.blacklab.analysis;

import java.io.Reader;

import org.apache.lucene.analysis.util.CharTokenizer;
import org.apache.lucene.util.AttributeFactory;

/**
 * A tokenizer that doesn't tokenize (returns the whole
 * field value as one token)
 */
public class BLNonTokenizer extends CharTokenizer {

	public BLNonTokenizer(AttributeFactory factory, Reader input) {
		super(factory, input);
	}

//	public BLNonTokenizer(AttributeSource source, Reader input) {
//		super(source, input);
//	}

	public BLNonTokenizer(Reader input) {
		super(input);
	}

	@Override
	protected boolean isTokenChar(int c) {
		return true;
	}

}
