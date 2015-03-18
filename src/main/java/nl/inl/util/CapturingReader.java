package nl.inl.util;

import java.io.IOException;
import java.io.Reader;

/**
 * Reader decorator that captures the content in a StringBuilder.
 */
public class CapturingReader extends Reader {

	private Reader input;

	private StringBuilder content = new StringBuilder();

	public CapturingReader(Reader input) {
		this.input = input;
	}

	@Override
	public int read(char[] cbuf, int off, int len) throws IOException {
		int charsRead = input.read(cbuf, off, len);
		if (charsRead > 0)
			content.append(cbuf, off, charsRead);
		return charsRead;
	}

	@Override
	public void close() throws IOException {
		input.close();
	}

	public String getContent() {
		return content.toString();
	}

}